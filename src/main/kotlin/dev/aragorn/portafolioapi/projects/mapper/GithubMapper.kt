package dev.aragorn.portafolioapi.projects.mapper

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import org.springframework.stereotype.Component
import java.time.OffsetDateTime


/**
 * Conversiones entre los DTOs de GitHub, la entidad [Project] y el DTO público [ProjectResponseDto].
 */
@Component
class GithubMapper {

    /**
     * Construye la entidad [Project] a partir de un repositorio enriquecido.
     *
     * Las fechas ISO-8601 que no se pueden leer se guardan como `null` en lugar de hacer fallar
     * la conversión.
     *
     * @param enriched repositorio con sus datos adicionales.
     * @param owner propietario ya persistido, que se asocia al proyecto.
     * @return la entidad lista para guardar.
     */
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

    /**
     * Convierte la entidad en el DTO público que devuelve `GET /projects`.
     *
     * Lee `project.owner`, así que el propietario tiene que estar cargado (ver
     * [dev.aragorn.portafolioapi.projects.repository.ProjectsRepository.findAll]).
     *
     * @param project entidad persistida.
     * @return el DTO de respuesta.
     */
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
