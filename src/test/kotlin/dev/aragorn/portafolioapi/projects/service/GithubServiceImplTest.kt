package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.client.GithubClient
import dev.aragorn.portafolioapi.projects.client.GithubRateLimitExceededException
import dev.aragorn.portafolioapi.projects.client.GithubServerException
import dev.aragorn.portafolioapi.projects.config.GithubProperties
import dev.aragorn.portafolioapi.projects.detector.TechStackDetector
import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.exceptions.InvalidGithubRepositoryException
import dev.aragorn.portafolioapi.projects.mapper.GithubMapper
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import dev.aragorn.portafolioapi.projects.repository.ProjectsRepository
import dev.aragorn.portafolioapi.projects.validator.GithubRepositoryValidator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class GithubServiceImplTest {
    @Mock
    private lateinit var client: GithubClient
    @Mock
    private lateinit var properties: GithubProperties
    @Mock
    private lateinit var repositoryValidator: GithubRepositoryValidator
    @Mock
    private lateinit var persistenceService: ProjectPersistenceService
    @Mock
    private lateinit var repositorio: ProjectsRepository
    @Mock
    private lateinit var mapper: GithubMapper
    @Mock
    private lateinit var techStackDetector: TechStackDetector
    @InjectMocks
    private lateinit var service: GithubServiceImpl

    private val login = "Aragorn7372"
    private val avatar = "https://avatars.github.com/u/1"
    private val owner1 = Owner(id = 1, name = login, avatarUrl = avatar)

    private fun repo(id: Long, name: String) = GithubRepositoryResponse(
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
    private val repo1 = repo(1L, "demo")
    private val repo2 = repo(2L, "otro")
    private val orgLogin = "mi-org"
    private val repo3 = GithubRepositoryResponse(
        id = 3L,
        name = "lib",
        fullName = "$orgLogin/lib",
        htmlUrl = "https://github.com/$orgLogin/lib",
        description = "lib repo",
        owner = GithubOwner(orgLogin, "https://avatars.github.com/u/2"),
        language = "Kotlin",
        stargazersCount = 7,
        forksCount = 2,
        topics = listOf("kotlin"),
        createdAt = "2024-02-01T00:00:00Z",
        updatedAt = "2024-07-01T00:00:00Z",
        pushedAt = null,
    )

    private val languagesBytes = mapOf("Kotlin" to 70L, "Java" to 30L)
    private val languages = mapOf("Kotlin" to 70.0, "Java" to 30.0)

    private fun enrichedOf(repository: GithubRepositoryResponse, pages: String) = EnrichedRepository(
        repository = repository,
        languages = languages,
        commits = 342,
        pagesUrl = "https://aragorn7372.github.io/$pages/",
    )
    private val enriched1 = enrichedOf(repo1, "demo")
    private val enriched2 = enrichedOf(repo2, "otro")
    private val enriched3 = EnrichedRepository(
        repository = repo3,
        languages = languages,
        commits = 342,
        pagesUrl = "https://mi-org.github.io/lib/",
    )

    private fun project(id: Long, name: String) = Project(
        id = id,
        name = name,
        fullName = "$login/$name",
        description = "$name repo",
        url = "https://github.com/$login/$name",
        owner = owner1,
        stars = 12,
        forks = 4,
        commits = 342,
        pagesUrl = "https://aragorn7372.github.io/$name/",
        languages = languages,
        topics = listOf("kotlin"),
    )
    private val project1 = project(1L, "demo")
    private val project2 = project(2L, "otro")

    private fun dto(name: String) = ProjectResponseDto(
        name = name,
        description = "$name repo",
        url = "https://github.com/$login/$name",
        pagesUrl = "https://aragorn7372.github.io/$name/",
        owner = login,
        avatarUrl = avatar,
        stars = 12,
        forks = 4,
        commits = 342,
        languages = languages,
        topics = listOf("kotlin"),
    )
    private val dto1 = dto("demo")
    private val dto2 = dto("otro")

    private fun givenProperties(
        excluded: Set<String> = emptySet(),
        organizations: List<String> = emptyList(),
    ) {
        whenever(properties.personal).thenReturn(login)
        whenever(properties.organizations).thenReturn(organizations)
        whenever(properties.excludedRepositories).thenReturn(excluded)
    }

    private suspend fun givenEnrichment(
        name: String,
        owner: String = login,
        pages: String = "https://aragorn7372.github.io/$name/",
    ) {
        whenever(client.findLanguages(owner, name)).thenReturn(languagesBytes)
        whenever(client.findCommitCount(owner, name)).thenReturn(342)
        whenever(client.findPagesUrl(owner, name)).thenReturn(pages)
        whenever(client.findRepositoryTree(owner, name, "HEAD")).thenReturn(emptyList())
    }

    @Test
    @DisplayName("refresh bien, todos nuevos ninguno en base de datos")
    fun refresh() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1, repo2))
        givenEnrichment("demo")
        givenEnrichment("otro")

        service.refresh()

        verify(client, times(1)).findUserRepositories(login)
        verify(client, times(0)).findOrganizationRepositories(any())

        verify(repositoryValidator, times(1)).validate(repo1)
        verify(repositoryValidator, times(1)).validate(repo2)

        verify(client, times(1)).findLanguages(login, "demo")
        verify(client, times(1)).findCommitCount(login, "demo")
        verify(client, times(1)).findPagesUrl(login, "demo")
        verify(client, times(1)).findRepositoryTree(login, "demo", "HEAD")
        verify(client, times(1)).findLanguages(login, "otro")

        verify(persistenceService, times(1)).replaceAll(listOf(enriched1, enriched2))
    }

    @Test
    @DisplayName("refresh with excluded repositories")
    fun refreshWithExcluded() = runTest {
        givenProperties(excluded = setOf("aragorn7372/demo"))
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1, repo2))
        givenEnrichment("otro")

        service.refresh()

        verify(client, times(1)).findUserRepositories(login)

        verify(repositoryValidator, times(0)).validate(repo1)
        verify(repositoryValidator, times(1)).validate(repo2)

        verify(client, times(0)).findLanguages(any(), eq("demo"))
        verify(client, times(1)).findLanguages(login, "otro")

        verify(persistenceService, times(1)).replaceAll(listOf(enriched2))
    }

    @Test
    @DisplayName("refresh with invalid repository")
    fun refreshWithInvalid() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1, repo2))
        doThrow(InvalidGithubRepositoryException("repo inválido"))
            .whenever(repositoryValidator).validate(repo1)
        givenEnrichment("otro")

        service.refresh()

        verify(repositoryValidator, times(1)).validate(repo1)
        verify(repositoryValidator, times(1)).validate(repo2)

        verify(client, times(0)).findLanguages(any(), eq("demo"))

        verify(persistenceService, times(1)).replaceAll(listOf(enriched2))
    }

    @Test
    @DisplayName("refresh bien, fallo de enrichment usa fallback")
    fun refreshWithEnrichmentFallback() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenThrow(GithubServerException("fallo languages"))
        whenever(client.findCommitCount(login, "demo")).thenReturn(342)
        whenever(client.findPagesUrl(login, "demo")).thenReturn("https://aragorn7372.github.io/demo/")
        whenever(client.findRepositoryTree(login, "demo", "HEAD")).thenReturn(emptyList())
        val enrichedFallback = enriched1.copy(languages = emptyMap())

        service.refresh()

        verify(persistenceService, times(1)).replaceAll(listOf(enrichedFallback))
    }

    @Test
    @DisplayName("refresh mal, rate limit aborta sin persistir")
    fun refreshRateLimit() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenReturn(languagesBytes)
        whenever(client.findCommitCount(login, "demo"))
            .thenThrow(GithubRateLimitExceededException(null, "rate limit"))

        assertThrows<GithubRateLimitExceededException> {
            service.refresh()
        }

        verify(persistenceService, times(0)).replaceAll(any())
    }

    @Test
    @DisplayName("refresh mal, personal en blanco lanza IllegalArgumentException")
    fun refreshBlankPersonal() = runTest {
        whenever(properties.personal).thenReturn("")

        val exception = assertThrows<IllegalArgumentException> {
            service.refresh()
        }

        assertEquals("APP_GITHUB_PERSONAL no está configurado", exception.message)
        verify(client, times(0)).findUserRepositories(any())
        verify(persistenceService, times(0)).replaceAll(any())
    }

    @Test
    @DisplayName("obtener todos los projects")
    fun getProjects() = runTest {
        whenever(repositorio.findAll()).thenReturn(listOf(project1, project2))
        whenever(mapper.toResponseDto(project1)).thenReturn(dto1)
        whenever(mapper.toResponseDto(project2)).thenReturn(dto2)

        val result = service.getProjects()

        assertEquals(listOf(dto1, dto2), result)
        verify(repositorio, times(1)).findAll()
        verify(mapper, times(1)).toResponseDto(project1)
        verify(mapper, times(1)).toResponseDto(project2)
    }

    @Test
    @DisplayName("refresh bien, combina personal y organizaciones")
    fun refreshWithOrganizations() = runTest {
        givenProperties(organizations = listOf("mi-org", "  ", ""))
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findOrganizationRepositories("mi-org")).thenReturn(listOf(repo3))
        givenEnrichment("demo")
        givenEnrichment("lib", owner = orgLogin, pages = "https://mi-org.github.io/lib/")

        service.refresh()

        verify(client, times(1)).findUserRepositories(login)
        verify(client, times(1)).findOrganizationRepositories("mi-org")
        verify(client, times(0)).findOrganizationRepositories("")
        verify(client, times(0)).findOrganizationRepositories("  ")

        verify(repositoryValidator, times(1)).validate(repo1)
        verify(repositoryValidator, times(1)).validate(repo3)

        verify(persistenceService, times(1)).replaceAll(listOf(enriched1, enriched3))
    }

    @Test
    @DisplayName("refresh bien, duplica en personal y org se persiste una vez")
    fun refreshDistinct() = runTest {
        givenProperties(organizations = listOf("mi-org"))
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findOrganizationRepositories("mi-org")).thenReturn(listOf(repo1))
        givenEnrichment("demo")

        service.refresh()

        verify(repositoryValidator, times(1)).validate(repo1)
        verify(client, times(1)).findLanguages(login, "demo")

        verify(persistenceService, times(1)).replaceAll(listOf(enriched1))
    }

    @Test
    @DisplayName("refresh bien, detecta tecnologías del árbol")
    fun refreshWithTechnologies() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenReturn(languagesBytes)
        whenever(client.findCommitCount(login, "demo")).thenReturn(342)
        whenever(client.findPagesUrl(login, "demo")).thenReturn("https://aragorn7372.github.io/demo/")
        val tree = listOf("package.json", "README.md")
        whenever(client.findRepositoryTree(login, "demo", "HEAD")).thenReturn(tree)
        whenever(techStackDetector.selectContentFiles(tree)).thenReturn(listOf("package.json"))
        whenever(client.findFileContent(login, "demo", "package.json", "HEAD"))
            .thenReturn("{\"name\":\"demo\"}")
        whenever(techStackDetector.detect(tree, mapOf("package.json" to "{\"name\":\"demo\"}")))
            .thenReturn(listOf("Node"))
        val enrichedTech = enriched1.copy(technologies = listOf("Node"))

        service.refresh()

        verify(techStackDetector, times(1)).selectContentFiles(tree)
        verify(client, times(1)).findFileContent(login, "demo", "package.json", "HEAD")
        verify(techStackDetector, times(1)).detect(tree, mapOf("package.json" to "{\"name\":\"demo\"}"))

        verify(persistenceService, times(1)).replaceAll(listOf(enrichedTech))
    }

    @Test
    @DisplayName("refresh bien, fallo de commits usa cero")
    fun refreshWithCommitFallback() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenReturn(languagesBytes)
        whenever(client.findCommitCount(login, "demo")).thenThrow(GithubServerException("fallo commits"))
        whenever(client.findPagesUrl(login, "demo")).thenReturn("https://aragorn7372.github.io/demo/")
        whenever(client.findRepositoryTree(login, "demo", "HEAD")).thenReturn(emptyList())
        val enrichedFallback = enriched1.copy(commits = 0)

        service.refresh()

        verify(persistenceService, times(1)).replaceAll(listOf(enrichedFallback))
    }

    @Test
    @DisplayName("refresh bien, fallo de pages usa null")
    fun refreshWithPagesFallback() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenReturn(languagesBytes)
        whenever(client.findCommitCount(login, "demo")).thenReturn(342)
        whenever(client.findPagesUrl(login, "demo")).thenThrow(GithubServerException("fallo pages"))
        whenever(client.findRepositoryTree(login, "demo", "HEAD")).thenReturn(emptyList())
        val enrichedFallback = enriched1.copy(pagesUrl = null)

        service.refresh()

        verify(persistenceService, times(1)).replaceAll(listOf(enrichedFallback))
    }

    @Test
    @DisplayName("refresh bien, fallo de tree salta el detector")
    fun refreshWithTreeFallback() = runTest {
        givenProperties()
        whenever(client.findUserRepositories(login)).thenReturn(listOf(repo1))
        whenever(client.findLanguages(login, "demo")).thenReturn(languagesBytes)
        whenever(client.findCommitCount(login, "demo")).thenReturn(342)
        whenever(client.findPagesUrl(login, "demo")).thenReturn("https://aragorn7372.github.io/demo/")
        whenever(client.findRepositoryTree(login, "demo", "HEAD"))
            .thenThrow(GithubServerException("fallo tree"))

        service.refresh()

        verify(techStackDetector, times(0)).selectContentFiles(any())
        verify(client, times(0)).findFileContent(any(), any(), any(), any())

        verify(persistenceService, times(1)).replaceAll(listOf(enriched1))
    }
}
