package dev.aragorn.portafolioapi.projects.client

import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.SocketTimeoutException

@ExtendWith(MockitoExtension::class)
class GithubClientImplTest {
    private val baseUrl = "https://api.github.com"
    private val builder = RestClient.builder()
        .baseUrl(baseUrl)
        .messageConverters { converters ->
            val mapper = JsonMapper.builder()
                .addModule(KotlinModule.Builder().build())
                .build()
            converters.add(0, JacksonJsonHttpMessageConverter(mapper))
        }
    private val mockServer = MockRestServiceServer.bindTo(builder).build()
    private val client = GithubClientImpl(builder.build())

    private val login = "Aragorn7372"
    private val avatar = "https://avatars.github.com/u/1"

    private fun repoDto(id: Long, name: String) = GithubRepositoryResponse(
        id = id,
        name = name,
        fullName = "$login/$name",
        htmlUrl = "https://github.com/$login/$name",
        description = "$name repo",
        owner = GithubOwner(login, avatar),
        language = "Kotlin",
        stargazersCount = 12,
        forksCount = 4,
        topics = listOf("kotlin"),
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-06-01T00:00:00Z",
        pushedAt = null,
    )
    private val repoDto1 = repoDto(1L, "demo")
    private val repoDto2 = repoDto(2L, "otro")

    private fun repoJson(id: Long, name: String) =
        """{"id":$id,"name":"$name","full_name":"$login/$name","html_url":"https://github.com/$login/$name","description":"$name repo","owner":{"login":"$login","avatar_url":"$avatar"},"language":"Kotlin","stargazers_count":12,"forks_count":4,"topics":["kotlin"],"created_at":"2024-01-01T00:00:00Z","updated_at":"2024-06-01T00:00:00Z","pushed_at":null}"""
    private val reposJson = "[${repoJson(1L, "demo")},${repoJson(2L, "otro")}]"
    private val reposUrl = "$baseUrl/users/$login/repos?per_page=100&page=1"
    private val languagesUrl = "$baseUrl/repos/$login/demo/languages"
    private val languagesJson = """{"Kotlin":70,"Java":30}"""
    private val languages = mapOf("Kotlin" to 70L, "Java" to 30L)

    @Test
    fun findUserRepositories() = runTest {
        mockServer.expect(requestTo(reposUrl))
            .andRespond(withSuccess(reposJson, MediaType.APPLICATION_JSON))

        val result = client.findUserRepositories(login)

        assertEquals(listOf(repoDto1, repoDto2), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("pagina hasta agotar y devuelve todos")
    fun findUserRepositoriesPages() = runTest {
        val page1 = (1..100).joinToString(",", "[", "]") { repoJson(it.toLong(), "repo-$it") }
        val page2 = "[${repoJson(101L, "repo-101")}]"
        mockServer.expect(requestTo(reposUrl))
            .andRespond(withSuccess(page1, MediaType.APPLICATION_JSON))
        mockServer.expect(requestTo("$baseUrl/users/$login/repos?per_page=100&page=2"))
            .andRespond(withSuccess(page2, MediaType.APPLICATION_JSON))

        val result = client.findUserRepositories(login)

        assertEquals(101, result.size)
        assertEquals("repo-101", result.last().name)
        mockServer.verify()
    }

    @Test
    @DisplayName("body vacío lanza GithubInvalidResponseException")
    fun findUserRepositoriesEmptyBody() = runTest {
        mockServer.expect(requestTo(reposUrl)).andRespond(withSuccess())

        val exception = assertThrows<GithubInvalidResponseException> {
            client.findUserRepositories(login)
        }

        assertEquals(
            "GitHub devolvió una respuesta vacía en /users/$login/repos page=1",
            exception.message
        )
        mockServer.verify()
    }

    @Test
    @DisplayName("404 lanza GithubNotFoundException")
    fun findUserRepositoriesNotFound() = runTest {
        mockServer.expect(requestTo(reposUrl))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThrows<GithubNotFoundException> {
            client.findUserRepositories(login)
        }

        mockServer.verify()
    }

    @Test
    fun findOrganizationRepositories() = runTest {
        mockServer.expect(requestTo("$baseUrl/orgs/mi-org/repos?per_page=100&page=1"))
            .andRespond(withSuccess(reposJson, MediaType.APPLICATION_JSON))

        val result = client.findOrganizationRepositories("mi-org")

        assertEquals(listOf(repoDto1, repoDto2), result)
        mockServer.verify()
    }

    @Test
    fun findLanguages() = runTest {
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(withSuccess(languagesJson, MediaType.APPLICATION_JSON))

        val result = client.findLanguages(login, "demo")

        assertEquals(languages, result)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 devuelve mapa vacío")
    fun findLanguagesNotFound() = runTest {
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = client.findLanguages(login, "demo")

        assertEquals(emptyMap<String, Long>(), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("cuota agotada lanza GithubRateLimitExceededException")
    fun findLanguagesRateLimit() = runTest {
        val headers = HttpHeaders()
        headers.set("X-RateLimit-Remaining", "0")
        headers.set("X-RateLimit-Reset", "2000000000")
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(withSuccess(languagesJson, MediaType.APPLICATION_JSON).headers(headers))

        assertThrows<GithubRateLimitExceededException> {
            client.findLanguages(login, "demo")
        }

        mockServer.verify()
    }

    @Test
    fun findCommitCount() = runTest {
        val headers = HttpHeaders()
        headers.set(
            HttpHeaders.LINK,
            "<$baseUrl/repos/$login/demo/commits?per_page=1&page=7>; rel=\"last\""
        )
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/commits?per_page=1"))
            .andRespond(withSuccess("[{}]", MediaType.APPLICATION_JSON).headers(headers))

        val result = client.findCommitCount(login, "demo")

        assertEquals(7, result)
        mockServer.verify()
    }

    @Test
    @DisplayName("sin cabecera Link devuelve el tamaño del body")
    fun findCommitCountNoLink() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/commits?per_page=1"))
            .andRespond(withSuccess("[{},{}]", MediaType.APPLICATION_JSON))

        val result = client.findCommitCount(login, "demo")

        assertEquals(2, result)
        mockServer.verify()
    }

    @Test
    @DisplayName("409 repo vacío devuelve cero")
    fun findCommitCountConflict() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/commits?per_page=1"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        val result = client.findCommitCount(login, "demo")

        assertEquals(0, result)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 devuelve cero")
    fun findCommitCountNotFound() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/commits?per_page=1"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = client.findCommitCount(login, "demo")

        assertEquals(0, result)
        mockServer.verify()
    }

    @Test
    fun findPagesUrl() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/pages"))
            .andRespond(withSuccess("""{"html_url":"https://aragorn7372.github.io/demo/"}""", MediaType.APPLICATION_JSON))

        val result = client.findPagesUrl(login, "demo")

        assertEquals("https://aragorn7372.github.io/demo/", result)
        mockServer.verify()
    }

    @Test
    @DisplayName("html_url en blanco devuelve null")
    fun findPagesUrlBlank() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/pages"))
            .andRespond(withSuccess("""{"html_url":""}""", MediaType.APPLICATION_JSON))

        val result = client.findPagesUrl(login, "demo")

        assertNull(result)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 devuelve null")
    fun findPagesUrlNotFound() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/pages"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = client.findPagesUrl(login, "demo")

        assertNull(result)
        mockServer.verify()
    }

    @Test
    fun findRepositoryTree() = runTest {
        val treeJson = """{"truncated":true,"tree":[{"path":"package.json","type":"blob"},{"path":"","type":"blob"}]}"""
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/git/trees/HEAD?recursive=1"))
            .andRespond(withSuccess(treeJson, MediaType.APPLICATION_JSON))

        val result = client.findRepositoryTree(login, "demo", "HEAD")

        assertEquals(listOf("package.json"), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 devuelve lista vacía")
    fun findRepositoryTreeNotFound() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/git/trees/HEAD?recursive=1"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = client.findRepositoryTree(login, "demo", "HEAD")

        assertEquals(emptyList<String>(), result)
        mockServer.verify()
    }

    @Test
    fun findFileContent() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/contents/package.json?ref=HEAD"))
            .andRespond(withSuccess("raw content", MediaType.TEXT_PLAIN))

        val result = client.findFileContent(login, "demo", "package.json", "HEAD")

        assertEquals("raw content", result)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 devuelve null")
    fun findFileContentNotFound() = runTest {
        mockServer.expect(requestTo("$baseUrl/repos/$login/demo/contents/package.json?ref=HEAD"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val result = client.findFileContent(login, "demo", "package.json", "HEAD")

        assertNull(result)
        mockServer.verify()
    }

    @Test
    @DisplayName("500 y luego éxito reintenta y devuelve el mapa")
    fun findLanguagesServerErrorRetry() = runTest {
        mockServer.expect(requestTo(languagesUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(withSuccess(languagesJson, MediaType.APPLICATION_JSON))

        val result = client.findLanguages(login, "demo")

        assertEquals(languages, result)
        mockServer.verify()
    }

    @Test
    @DisplayName("500 tres veces lanza GithubServerException")
    fun findLanguagesServerError() = runTest {
        mockServer.expect(requestTo(languagesUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(languagesUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(languagesUrl)).andRespond(withServerError())

        val exception = assertThrows<GithubServerException> {
            client.findLanguages(login, "demo")
        }

        assertEquals("GitHub devolvió 500 INTERNAL_SERVER_ERROR", exception.message)
        mockServer.verify()
    }

    @Test
    @DisplayName("timeout tres veces lanza GithubTimeoutException")
    fun findLanguagesTimeout() = runTest {
        mockServer.expect(requestTo(languagesUrl)).andRespond(withException(SocketTimeoutException("timeout")))
        mockServer.expect(requestTo(languagesUrl)).andRespond(withException(SocketTimeoutException("timeout")))
        mockServer.expect(requestTo(languagesUrl)).andRespond(withException(SocketTimeoutException("timeout")))

        val exception = assertThrows<GithubTimeoutException> {
            client.findLanguages(login, "demo")
        }

        assertTrue(exception.message!!.contains("Timeout comunicando con GitHub"))
        mockServer.verify()
    }

    @Test
    @DisplayName("401 lanza GithubUnauthorizedException")
    fun findLanguagesUnauthorized() = runTest {
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val exception = assertThrows<GithubUnauthorizedException> {
            client.findLanguages(login, "demo")
        }

        assertEquals("Token de GitHub inválido o ausente. Revisa APP_GITHUB_TOKEN.", exception.message)
        mockServer.verify()
    }

    @Test
    @DisplayName("403 con rate limit lanza GithubRateLimitExceededException")
    fun findLanguagesRateLimitStatus() = runTest {
        mockServer.expect(requestTo(languagesUrl))
            .andRespond(
                withStatus(HttpStatus.FORBIDDEN)
                    .body("rate limit exceeded")
                    .contentType(MediaType.APPLICATION_JSON)
            )

        assertThrows<GithubRateLimitExceededException> {
            client.findLanguages(login, "demo")
        }

        mockServer.verify()
    }
}
