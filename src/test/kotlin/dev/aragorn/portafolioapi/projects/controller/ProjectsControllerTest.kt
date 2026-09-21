package dev.aragorn.portafolioapi.projects.controller

import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.service.GithubService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class ProjectsControllerTest {
    @Mock
    private lateinit var githubService: GithubService

    @InjectMocks
    private lateinit var controller: ProjectsController

    private fun dto(name: String) = ProjectResponseDto(
        name = name,
        description = "$name repo",
        url = "https://github.com/Aragorn7372/$name",
        pagesUrl = "https://aragorn7372.github.io/$name/",
        owner = "Aragorn7372",
        avatarUrl = "https://avatars.github.com/u/1",
        stars = 12,
        forks = 4,
        commits = 342,
        languages = mapOf("Kotlin" to 100.0),
        topics = listOf("kotlin"),
    )
    private val dto1 = dto("demo")
    private val dto2 = dto("otro")

    @Test
    @DisplayName("obtener todos los projects")
    fun getAllProjects() = runTest {
        whenever(githubService.getProjects()).thenReturn(listOf(dto1, dto2))

        val result = controller.getAllProjects()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf(dto1, dto2), result.body)
        verify(githubService, times(1)).getProjects()
    }
}
