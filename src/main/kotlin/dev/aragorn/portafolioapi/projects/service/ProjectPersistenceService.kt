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
 * Puntos 14+20+21: persiste el último estado válido conocido.
 * Solo se invoca al final de un refresh completamente exitoso:
 * upsert por ID de GitHub + borrado de los que ya no existen (o pasaron a excluidos).
 */
@Service
class ProjectPersistenceService(
    private val projectsRepository: ProjectsRepository,
    private val ownersRepository: OwnersRepository,
    private val mapper: GithubMapper,
) {

    /**
     * Punto 23: la cache solo se invalida si la persistencia fue exitosa.
     * Si la transacción hace rollback (excepción), el evict no se ejecuta.
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
