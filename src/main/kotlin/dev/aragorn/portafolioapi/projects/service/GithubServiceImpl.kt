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
        private const val MAX_CONCURRENT_REPOSITORIES = 3
    }

    private val githubDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_REPOSITORIES)

    override suspend fun refresh() {
        val repositories = fetchCombinedRepositories()
        val enriched = enrichRepositories(repositories)
        val withPages = enriched.count { it.pagesUrl != null }
        // Punto 19: solo se llega aquí si GitHub respondió bien y todo validó.
        // Cualquier excepción anterior aborta sin tocar DB ni cache.
        withContext(Dispatchers.IO) {
            persistenceService.replaceAll(enriched)
        }
        log.info("Refresh GitHub persistido: ${enriched.size} proyectos ($withPages con Pages).")
    }

    /**
     * Punto 22: Caffeine → Redis → PostgreSQL. La cache es solo optimización;
     * si Redis/Caffeine fallan o están vacíos, se sirve desde PostgreSQL.
     */
    @Cacheable(cacheNames = ["projects"])
    override suspend fun getProjects(): List<ProjectResponseDto> =
        withContext(Dispatchers.IO) {
            projectsRepository.findAll().map(mapper::toResponseDto)
        }

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


    private suspend fun enrichRepositories(repositories: List<GithubRepositoryResponse>): List<EnrichedRepository> =
        coroutineScope {
            repositories.map { repository ->
                async(githubDispatcher) { enrich(repository) }
            }.awaitAll()
        }

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
