package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.mapper.GithubMapper
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.repository.OwnersRepository
import dev.aragorn.portafolioapi.projects.repository.ProjectsRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class ProjectPersistenceService(
    private val projectsRepository: ProjectsRepository,
    private val ownersRepository: OwnersRepository,
    private val mapper: GithubMapper,
) {


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
