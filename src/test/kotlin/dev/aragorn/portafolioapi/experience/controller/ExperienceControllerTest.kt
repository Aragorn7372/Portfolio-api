package dev.aragorn.portafolioapi.experience.controller

import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceImageResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceNotFoundException
import dev.aragorn.portafolioapi.experience.service.ExperienceService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
class ExperienceControllerTest {
    @Mock
    private lateinit var service: ExperienceService

    @InjectMocks
    private lateinit var controller: ExperienceController

    private val experienceDto1 = ExperienceResponseDto(
        "acme",
        "Backend Developer",
        "Acme",
        "Madrid",
        "https://cdn.test/logo.png"
    )
    private val experienceDto2 = ExperienceResponseDto(
        "globex",
        "Fullstack Developer",
        "Globex",
        "Remoto",
        null
    )
    private val experienceDetailsDto = ExperienceDetailsResponseDto(
        "acme",
        "Backend Developer",
        "Acme",
        "Madrid",
        "# Backend Developer",
        listOf(ExperienceImageResponseDto("logo.png", "https://cdn.test/logo.png"))
    )

    @Test
    @DisplayName("obtener todas las experiencias")
    fun getAllExperiences() = runTest {
        whenever(service.getExperiences()).thenReturn(listOf(experienceDto1, experienceDto2))

        val result = controller.getAllExperiences()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf(experienceDto1, experienceDto2), result.body)
        verify(service, times(1)).getExperiences()
    }

    @Test
    @DisplayName("obtener experiencia por id")
    fun getExperienceById() = runTest {
        whenever(service.getExperience("acme")).thenReturn(experienceDetailsDto)

        val result = controller.getExperienceById("acme")

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(experienceDetailsDto, result.body)
        verify(service, times(1)).getExperience("acme")
    }

    @Test
    @DisplayName("obtener experiencia por id mal, no existe propaga ExperienceNotFoundException")
    fun getExperienceByIdNotFound() = runTest {
        whenever(service.getExperience("no-existe"))
            .thenThrow(ExperienceNotFoundException("Experience not found"))

        val exception = assertThrows<ExperienceNotFoundException> {
            controller.getExperienceById("no-existe")
        }

        assertEquals("Experience not found", exception.message)
        verify(service, times(1)).getExperience("no-existe")
    }
}
