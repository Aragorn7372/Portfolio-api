package dev.aragorn.portafolioapi.projects.mapper

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import org.springframework.stereotype.Component
import java.time.OffsetDateTime


@Component
class GithubMapper {

    fun toProject(enriched: EnrichedRepository, owner: Owner): Project {
        val repository = enriched.repository
        return Project(
            id = repository.id,
            name = repository.name,
            fullName = repository.fullName,
            description = repository.description,
            url = repository.htmlUrl,
            owner = owner,
            stars = repository.stargazersCount,
            forks = repository.forksCount,
            commits = enriched.commits,
            pagesUrl = enriched.pagesUrl,
            languages = enriched.languages,
            topics = repository.topics,
            technologies = enriched.technologies,
            fork = repository.fork,
            createdAt = parseDateTime(repository.createdAt),
            updatedAt = parseDateTime(repository.updatedAt),
            pushedAt = parseDateTime(repository.pushedAt),
        )
    }

    private fun parseDateTime(value: String?): OffsetDateTime? =
        value?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    fun toResponseDto(project: Project): ProjectResponseDto =
        ProjectResponseDto(
            name = project.name,
            description = project.description,
            url = project.url,
            pagesUrl = project.pagesUrl,
            owner = project.owner.name,
            avatarUrl = project.owner.avatarUrl,
            stars = project.stars,
            forks = project.forks,
            commits = project.commits,
            languages = project.languages,
            topics = project.topics,
            technologies = project.technologies,
        )
}
