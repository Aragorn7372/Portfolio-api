package dev.aragorn.portafolioapi.projects.mapper

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.OffsetDateTime

class GithubMapperTest {
    private val mapper = GithubMapper()
    private val owner1 = Owner(
        id = 1,
        name = "Aragorn7372",
        avatarUrl = "https://avatars.github.com/u/1"
    )
    private val enriched1 = EnrichedRepository(
        repository = GithubRepositoryResponse(
            id = 123L,
            name = "demo",
            fullName = "Aragorn7372/demo",
            htmlUrl = "https://github.com/Aragorn7372/demo",
            description = "demo repo",
            fork = false,
            owner = GithubOwner("Aragorn7372", "https://avatars.github.com/u/1"),
            language = "Kotlin",
            stargazersCount = 12,
            forksCount = 4,
            topics = listOf("kotlin", "spring"),
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-06-01T00:00:00Z",
            pushedAt = null,
        ),
        languages = mapOf("Kotlin" to 100.0),
        commits = 342,
        pagesUrl = "https://aragorn7372.github.io/demo/",
    )
    private val project1 = Project(
        id = 123L,
        name = "demo",
        fullName = "Aragorn7372/demo",
        description = "demo repo",
        url = "https://github.com/Aragorn7372/demo",
        owner = owner1,
        stars = 12,
        forks = 4,
        commits = 342,
        pagesUrl = "https://aragorn7372.github.io/demo/",
        fork = false,
        topics = listOf("kotlin", "spring"),
        createdAt = OffsetDateTime.parse("2024-01-01T00:00:00Z"),
        updatedAt = OffsetDateTime.parse("2024-06-01T00:00:00Z"),
        pushedAt = null,
        languages = mapOf("Kotlin" to 100.0),
    )
    private val projectDto1 = ProjectResponseDto(
        name = "demo",
        description = "demo repo",
        url = "https://github.com/Aragorn7372/demo",
        pagesUrl = "https://aragorn7372.github.io/demo/",
        owner = "Aragorn7372",
        avatarUrl = "https://avatars.github.com/u/1",
        stars = 12,
        forks = 4,
        commits = 342,
        languages = mapOf("Kotlin" to 100.0),
        topics = listOf("kotlin", "spring"),
    )
    private val enriched2 = enriched1.copy(
        repository = enriched1.repository.copy(
            createdAt = "buenasnoches",
            updatedAt = "buenasnoches",
            pushedAt = "buenasnoches",
        )
    )

    @Test
    fun toProject() {
        val result = mapper.toProject(enriched1, owner1)
        assertEquals(project1, result)
    }

    @Test
    fun toResponseDto() {
        val result = mapper.toResponseDto(project1)
        assertEquals(projectDto1, result)
    }

    @Test
    @DisplayName("to project con fecha incorrecta")
    fun toProjectBad() {
        val result = assertDoesNotThrow {
            mapper.toProject(enriched2, owner1)
        }

        assertNull(result.createdAt)
        assertNull(result.updatedAt)
        assertNull(result.pushedAt)
        assertEquals(project1.copy(createdAt = null, updatedAt = null, pushedAt = null), result)
    }
}
