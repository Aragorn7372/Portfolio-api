package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.mapper.GithubMapper
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.repository.OwnersRepository
import dev.aragorn.portafolioapi.projects.repository.ProjectsRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


/**
 * Guarda el resultado de un refresco de proyectos.
 *
 * Es un bean aparte de [GithubServiceImpl] a propósito: `@Transactional` y `@CacheEvict`
 * funcionan con proxies de Spring, que no se aplican si un método se llama desde la misma clase.
 * Además, así la transacción es corta y bloqueante, y no se mezcla con las llamadas suspend a GitHub.
 *
 * @param projectsRepository repositorio de proyectos.
 * @param ownersRepository repositorio de propietarios.
 * @param mapper conversor a entidad.
 */
@Service
class ProjectPersistenceService(
    private val projectsRepository: ProjectsRepository,
    private val ownersRepository: OwnersRepository,
    private val mapper: GithubMapper,
) {


    /**
     * Sustituye los proyectos guardados por los del refresco, en una sola transacción.
     *
     * - Borra los proyectos que ya no están (repositorio eliminado, excluido o que ha dejado de ser válido).
     * - Inserta o actualiza los demás por su id de GitHub.
     * - Crea los propietarios nuevos y actualiza el avatar de los que ya existían.
     * - Al terminar, vacía la caché `projects`.
     *
     * @param enriched repositorios del refresco.
     */
    @CacheEvict(cacheNames = ["projects"], allEntries = true)
    @Transactional
    fun replaceAll(enriched: List<EnrichedRepository>) {
        val projects = enriched.map { item ->
            mapper.toProject(item, resolveOwner(item.repository.owner.login, item.repository.owner.avatarUrl))
        }
        val incomingIds = projects.map { it.id }.toSet()

        projectsRepository.findAll()
            .filter { it.id !in incomingIds }
            .takeIf { it.isNotEmpty() }
            ?.let { projectsRepository.deleteAll(it) }

        projectsRepository.saveAll(projects)
    }

    /**
     * Devuelve el propietario persistido con ese login y lo crea o actualiza su avatar si hace falta.
     *
     * @param login login en GitHub.
     * @param avatarUrl avatar actual.
     * @return el propietario ya guardado.
     */
    private fun resolveOwner(login: String, avatarUrl: String): Owner {
        val existing = ownersRepository.findByName(login)
        if (existing != null) {
            return if (existing.avatarUrl != avatarUrl) {
                ownersRepository.save(existing.copy(avatarUrl = avatarUrl))
            } else {
                existing
            }
        }
        return ownersRepository.save(Owner(name = login, avatarUrl = avatarUrl))
    }
}
