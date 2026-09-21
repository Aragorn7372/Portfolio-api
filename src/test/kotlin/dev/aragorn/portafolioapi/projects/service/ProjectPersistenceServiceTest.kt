package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.dto.EnrichedRepository
import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.mapper.GithubMapper
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import dev.aragorn.portafolioapi.projects.repository.OwnersRepository
import dev.aragorn.portafolioapi.projects.repository.ProjectsRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ProjectPersistenceServiceTest {
    @Mock
    private lateinit var projectsRepository: ProjectsRepository
    @Mock
    private lateinit var ownersRepository: OwnersRepository
    @Mock
    private lateinit var mapper: GithubMapper
    @InjectMocks
    private lateinit var service: ProjectPersistenceService

    private val login = "Aragorn7372"
    private val avatar = "https://avatars.github.com/u/1"
    private val owner1 = Owner(id = 1, name = login, avatarUrl = avatar)
    private val ownerOld = owner1.copy(avatarUrl = "https://avatars.github.com/u/old")

    private fun enriched(id: Long, name: String) = EnrichedRepository(
        repository = GithubRepositoryResponse(
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
        ),
        languages = mapOf("Kotlin" to 100.0),
        commits = 342,
        pagesUrl = "https://aragorn7372.github.io/$name/",
    )
    private val enriched1 = enriched(123L, "demo")
    private val enriched2 = enriched(456L, "otro")

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
        languages = mapOf("Kotlin" to 100.0),
        topics = listOf("kotlin"),
    )
    private val project1 = project(123L, "demo")
    private val project2 = project(456L, "otro")
    private val project3 = project(789L, "viejo")

    @Test
    @DisplayName("replaceAll bien, todos nuevos ninguno en base de datos")
    fun replaceAll() {
        whenever(ownersRepository.findByName(login)).thenReturn(null)
        whenever(ownersRepository.save(Owner(name = login, avatarUrl = avatar))).thenReturn(owner1)
        whenever(mapper.toProject(enriched1, owner1)).thenReturn(project1)
        whenever(mapper.toProject(enriched2, owner1)).thenReturn(project2)
        whenever(projectsRepository.findAll()).thenReturn(listOf())
        whenever(projectsRepository.saveAll(listOf(project1, project2)))
            .thenReturn(mutableListOf(project1, project2))

        service.replaceAll(listOf(enriched1, enriched2))

        verify(ownersRepository, times(2)).findByName(login)
        verify(ownersRepository, times(2)).save(Owner(name = login, avatarUrl = avatar))

        verify(mapper, times(1)).toProject(enriched1, owner1)
        verify(mapper, times(1)).toProject(enriched2, owner1)

        verify(projectsRepository, times(1)).findAll()

        verify(projectsRepository, times(0)).deleteAll(any())

        verify(projectsRepository, times(1)).saveAll(listOf(project1, project2))
    }

    @Test
    @DisplayName("replaceAll with deleted projects")
    fun replaceAllWithDeleted() {
        whenever(ownersRepository.findByName(login)).thenReturn(owner1)
        whenever(mapper.toProject(enriched1, owner1)).thenReturn(project1)
        whenever(projectsRepository.findAll()).thenReturn(listOf(project1, project3))
        whenever(projectsRepository.saveAll(listOf(project1))).thenReturn(mutableListOf(project1))

        service.replaceAll(listOf(enriched1))

        verify(ownersRepository, times(1)).findByName(login)
        verify(ownersRepository, times(0)).save(any())

        verify(mapper, times(1)).toProject(enriched1, owner1)

        verify(projectsRepository, times(1)).findAll()

        verify(projectsRepository, times(1)).deleteAll(listOf(project3))

        verify(projectsRepository, times(1)).saveAll(listOf(project1))
    }

    @Test
    @DisplayName("replaceAll bien, owner existente con mismo avatar no lo guarda")
    fun replaceAllExistingOwner() {
        whenever(ownersRepository.findByName(login)).thenReturn(owner1)
        whenever(mapper.toProject(enriched1, owner1)).thenReturn(project1)
        whenever(projectsRepository.findAll()).thenReturn(listOf(project1))
        whenever(projectsRepository.saveAll(listOf(project1))).thenReturn(mutableListOf(project1))

        service.replaceAll(listOf(enriched1))

        verify(ownersRepository, times(1)).findByName(login)
        verify(ownersRepository, times(0)).save(any())

        verify(mapper, times(1)).toProject(enriched1, owner1)

        verify(projectsRepository, times(1)).findAll()
        verify(projectsRepository, times(0)).deleteAll(any())
        verify(projectsRepository, times(1)).saveAll(listOf(project1))
    }

    @Test
    @DisplayName("replaceAll bien, owner con avatar cambiado lo actualiza")
    fun replaceAllOwnerAvatarChanged() {
        val updated = ownerOld.copy(avatarUrl = avatar)
        whenever(ownersRepository.findByName(login)).thenReturn(ownerOld)
        whenever(ownersRepository.save(updated)).thenReturn(owner1)
        whenever(mapper.toProject(enriched1, owner1)).thenReturn(project1)
        whenever(projectsRepository.findAll()).thenReturn(listOf(project1))
        whenever(projectsRepository.saveAll(listOf(project1))).thenReturn(mutableListOf(project1))

        service.replaceAll(listOf(enriched1))

        verify(ownersRepository, times(1)).findByName(login)
        verify(ownersRepository, times(1)).save(updated)

        verify(mapper, times(1)).toProject(enriched1, owner1)

        verify(projectsRepository, times(1)).findAll()
        verify(projectsRepository, times(0)).deleteAll(any())
        verify(projectsRepository, times(1)).saveAll(listOf(project1))
    }
}
