package dev.aragorn.portafolioapi.projects.dto

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class GithubDeserializationTest {
    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
    private val repo1 = GithubRepositoryResponse(
        id = 123L,
        name = "demo",
        fullName = "Aragorn7372/demo",
        htmlUrl = "https://github.com/Aragorn7372/demo",
        description = "demo repo",
        owner = GithubOwner("Aragorn7372", "https://avatars.github.com/u/1"),
        language = "Kotlin",
        stargazersCount = 12,
        forksCount = 4,
        topics = listOf("kotlin", "spring"),
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-06-01T00:00:00Z",
        pushedAt = null,
        defaultBranch = "main",
    )

    @Test
    @DisplayName("deserializa snake_case e ignora desconocidos")
    fun deserializeRepo() {
        val json = """
            {
                "id": 123,
                "name": "demo",
                "full_name": "Aragorn7372/demo",
                "html_url": "https://github.com/Aragorn7372/demo",
                "description": "demo repo",
                "fork": false,
                "owner": {"login": "Aragorn7372", "avatar_url": "https://avatars.github.com/u/1"},
                "language": "Kotlin",
                "stargazers_count": 12,
                "forks_count": 4,
                "topics": ["kotlin", "spring"],
                "created_at": "2024-01-01T00:00:00Z",
                "updated_at": "2024-06-01T00:00:00Z",
                "pushed_at": null,
                "default_branch": "main",
                "node_id": "ignorado"
            }
        """.trimIndent()

        val result = mapper.readValue(json, GithubRepositoryResponse::class.java)

        assertEquals(repo1, result)
    }

    @Test
    @DisplayName("ausentes usan defaults")
    fun deserializeDefaults() {
        val json = """
            {
                "id": 123,
                "name": "demo",
                "full_name": "Aragorn7372/demo",
                "html_url": "https://github.com/Aragorn7372/demo",
                "description": null,
                "owner": {"login": "Aragorn7372", "avatar_url": "https://avatars.github.com/u/1"},
                "language": null,
                "created_at": null,
                "updated_at": null,
                "pushed_at": null
            }
        """.trimIndent()

        val result = mapper.readValue(json, GithubRepositoryResponse::class.java)

        assertEquals(0, result.stargazersCount)
        assertEquals(0, result.forksCount)
        assertEquals(emptyList<String>(), result.topics)
        assertEquals(false, result.fork)
    }

    @Test
    @DisplayName("deserializa árbol y pages")
    fun deserializeTreeAndPages() {
        val tree = mapper.readValue(
            """{"truncated":true,"tree":[{"path":"package.json","type":"blob"}]}""",
            GithubTreeResponse::class.java
        )
        val pages = mapper.readValue(
            """{"html_url":"https://aragorn7372.github.io/demo/"}""",
            GithubPagesResponse::class.java
        )

        assertEquals(GithubTreeResponse(truncated = true, tree = listOf(GithubTreeNode("package.json", "blob"))), tree)
        assertEquals(GithubPagesResponse("https://aragorn7372.github.io/demo/"), pages)
    }
}
