package dev.aragorn.portafolioapi.projects.mapper

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GithubMapperTest {

    private val mapper = GithubMapper()

    @Test
    fun `mapea respuesta de GitHub a Project`() {
        val enriched = EnrichedRepository(
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
        val owner = Owner(id = 1, name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1")

        val project = mapper.toProject(enriched, owner)

        assertEquals(123L, project.id)
        assertEquals("demo", project.name)
        assertEquals("https://github.com/Aragorn7372/demo", project.url)
        assertEquals(owner, project.owner)
        assertEquals(12, project.stars)
        assertEquals(342, project.commits)
        assertEquals("https://aragorn7372.github.io/demo/", project.pagesUrl)
        assertEquals(listOf("kotlin", "spring"), project.topics)
        assertEquals("2024-01-01T00:00Z", project.createdAt.toString())
        assertNull(project.pushedAt)
    }

    @Test
    fun `mapea Project a DTO del frontend`() {
        val project = Project(
            id = 123L,
            name = "demo",
            fullName = "Aragorn7372/demo",
            description = "demo repo",
            url = "https://github.com/Aragorn7372/demo",
            owner = Owner(id = 1, name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1"),
            stars = 12,
            forks = 4,
            commits = 342,
            pagesUrl = "https://aragorn7372.github.io/demo/",
            languages = mapOf("Kotlin" to 100.0),
            topics = listOf("kotlin"),
        )

        val dto = mapper.toResponseDto(project)

        assertEquals("demo", dto.name)
        assertEquals("Aragorn7372", dto.owner)
        assertEquals("https://avatars.github.com/u/1", dto.avatarUrl)
        assertEquals(342, dto.commits)
        assertEquals("https://aragorn7372.github.io/demo/", dto.pagesUrl)
        assertEquals(mapOf("Kotlin" to 100.0), dto.languages)
    }
}
