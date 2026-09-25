package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.client.GithubClient
import dev.aragorn.portafolioapi.projects.client.GithubException
import dev.aragorn.portafolioapi.projects.client.GithubRateLimitExceededException
import dev.aragorn.portafolioapi.projects.config.GithubProperties
import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.dto.toPercentages
import dev.aragorn.portafolioapi.projects.exceptions.InvalidGithubRepositoryException
import dev.aragorn.portafolioapi.projects.detector.TechStackDetector
import dev.aragorn.portafolioapi.projects.mapper.GithubMapper
import dev.aragorn.portafolioapi.projects.repository.ProjectsRepository
import dev.aragorn.portafolioapi.projects.validator.GithubRepositoryValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.logging.Logger

/**
 * Implementación de [GithubService].
 *
 * ## Flujo de [refresh]
 * 1. **Recopilar:** repositorios del usuario (`app.github.personal`) y de cada organización
 *    (`app.github.organizations`).
 * 2. **Filtrar:** quitar duplicados por id, quitar los excluidos (`app.github.excluded-repositories`)
 *    y descartar los que no pasan [GithubRepositoryValidator].
 * 3. **Enriquecer:** por cada repositorio se consultan lenguajes, commits, Pages, el árbol de
 *    ficheros y unos pocos ficheros de configuración para [TechStackDetector]. Se procesan como
 *    mucho [MAX_CONCURRENT_REPOSITORIES] repositorios a la vez, para no disparar los límites de
 *    abuso de GitHub.
 * 4. **Persistir:** [ProjectPersistenceService.replaceAll] sustituye todos los proyectos en una
 *    transacción y vacía la caché `projects`.
 *
 * Si falla un dato de detalle de un repositorio, se usa un valor por defecto para ese dato y el
 * refresco continúa (ver [withRepoFallback]). Si se agota la cuota, el refresco se aborta sin
 * persistir nada, así no se guarda una foto a medias.
 *
 * @param githubClient cliente de la API de GitHub.
 * @param properties configuración del módulo.
 * @param repositoryValidator validador de repositorios.
 * @param persistenceService persistencia transaccional de proyectos.
 * @param projectsRepository repositorio para las lecturas.
 * @param mapper conversor entidad → DTO.
 * @param techStackDetector detector de tecnologías.
 */
@Service
class GithubServiceImpl(
    private val githubClient: GithubClient,
    private val properties: GithubProperties,
    private val repositoryValidator: GithubRepositoryValidator,
    private val persistenceService: ProjectPersistenceService,
    private val projectsRepository: ProjectsRepository,
    private val mapper: GithubMapper,
    private val techStackDetector: TechStackDetector,
) : GithubService {

    private val log: Logger = Logger.getLogger(GithubServiceImpl::class.java.name)

    companion object {
        /** Número máximo de repositorios que se enriquecen a la vez. */
        private const val MAX_CONCURRENT_REPOSITORIES = 3
    }

    /** Dispatcher de IO limitado a [MAX_CONCURRENT_REPOSITORIES] hilos para el enriquecimiento. */
    private val githubDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_REPOSITORIES)

    override suspend fun refresh() {
        val repositories = fetchCombinedRepositories()
        val enriched = enrichRepositories(repositories)
        val withPages = enriched.count { it.pagesUrl != null }
        withContext(Dispatchers.IO) {
            persistenceService.replaceAll(enriched)
        }
        log.info("Refresh GitHub persistido: ${enriched.size} proyectos ($withPages con Pages).")
    }


    /**
     * Lee los proyectos de la base de datos y los guarda en la caché `projects`.
     *
     * Como la caché no tiene clave de parámetros, todo el listado se guarda como una única
     * entrada. Se invalida en cada refresco.
     */
    @Cacheable(cacheNames = ["projects"])
    override suspend fun getProjects(): List<ProjectResponseDto> =
        withContext(Dispatchers.IO) {
            projectsRepository.findAll().map(mapper::toResponseDto)
        }

    /**
     * Junta los repositorios del usuario y de las organizaciones, quita duplicados y aplica
     * exclusiones y validación.
     *
     * @return los repositorios que entran en el portafolio.
     * @throws IllegalArgumentException si `app.github.personal` está vacío.
     */
    private suspend fun fetchCombinedRepositories(): List<GithubRepositoryResponse> {
        val personal = properties.personal.trim()
        require(personal.isNotBlank()) { "APP_GITHUB_PERSONAL no está configurado" }

        val personalRepos = githubClient.findUserRepositories(personal)

        val organizationRepos = mutableListOf<GithubRepositoryResponse>()
        properties.organizations
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { organization ->
                organizationRepos.addAll(githubClient.findOrganizationRepositories(organization))
            }

        val excluded = properties.excludedRepositories
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        return (personalRepos + organizationRepos)
            .distinctBy { it.id }
            .filter { repository ->
                val key = "${repository.owner.login}/${repository.name}"
                if (key.lowercase() in excluded) {
                    log.info("Repositorio excluido por configuración: $key")
                    return@filter false
                }
                try {
                    repositoryValidator.validate(repository)
                    true
                } catch (ex: InvalidGithubRepositoryException) {
                    log.warning("Repositorio descartado ${repository.fullName}: ${ex.message}")
                    false
                }
            }
    }


    /**
     * Enriquece todos los repositorios en paralelo (limitado por [githubDispatcher]).
     *
     * Si un repositorio lanza [GithubRateLimitExceededException], se cancela el resto.
     */
    private suspend fun enrichRepositories(repositories: List<GithubRepositoryResponse>): List<EnrichedRepository> =
        coroutineScope {
            repositories.map { repository ->
                async(githubDispatcher) { enrich(repository) }
            }.awaitAll()
        }

    /**
     * Consulta los datos adicionales de un repositorio.
     *
     * Se trabaja sobre la rama por defecto (o `HEAD` si no viene). Si no se pudo leer el árbol, no
     * se descarga ningún fichero y las tecnologías quedan vacías.
     *
     * @param repository repositorio del listado.
     * @return el repositorio con sus datos adicionales.
     */
    private suspend fun enrich(repository: GithubRepositoryResponse): EnrichedRepository {
        val owner = repository.owner.login
        val key = "$owner/${repository.name}"
        val ref = repository.defaultBranch?.takeIf { it.isNotBlank() } ?: "HEAD"
        val languages = withRepoFallback(key, "languages", emptyMap()) {
            githubClient.findLanguages(owner, repository.name).toPercentages()
        }
        val commits = withRepoFallback(key, "commits", 0) {
            githubClient.findCommitCount(owner, repository.name)
        }
        val pagesUrl = withRepoFallback(key, "pages", null) {
            githubClient.findPagesUrl(owner, repository.name)
        }
        val tree = withRepoFallback(key, "tree", emptyList()) {
            githubClient.findRepositoryTree(owner, repository.name, ref)
        }
        val technologies = if (tree.isEmpty()) {
            emptyList()
        } else {
            val contentFiles = techStackDetector.selectContentFiles(tree)
            val contents = contentFiles.associateWith { path ->
                withRepoFallback(key, "contents:$path", null) {
                    githubClient.findFileContent(owner, repository.name, path, ref)
                }
            }.mapNotNull { (path, content) -> content?.let { path to it } }.toMap()
            techStackDetector.detect(tree, contents)
        }
        return EnrichedRepository(repository, languages, commits, pagesUrl, technologies)
    }

    /**
     * Ejecuta una consulta de detalle y devuelve [fallback] si falla, salvo que se haya agotado la cuota.
     *
     * @param repositoryKey `propietario/nombre`, para el log.
     * @param what qué dato se consulta, para el log.
     * @param fallback valor que se usa si la consulta falla.
     * @param action consulta a ejecutar.
     * @return el resultado de la consulta, o [fallback].
     * @throws GithubRateLimitExceededException se propaga siempre para cortar el refresco.
     */
    private suspend fun <T> withRepoFallback(
        repositoryKey: String,
        what: String,
        fallback: T,
        action: suspend () -> T,
    ): T {
        return try {
            action()
        } catch (ex: GithubRateLimitExceededException) {
            throw ex
        } catch (ex: GithubException) {
            log.warning("Sin $what para $repositoryKey (${ex.message}); se usa valor por defecto")
            fallback
        }
    }
}
