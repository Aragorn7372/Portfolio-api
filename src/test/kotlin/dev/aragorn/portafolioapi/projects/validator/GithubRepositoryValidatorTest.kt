package dev.aragorn.portafolioapi.projects.validator

import dev.aragorn.portafolioapi.projects.dto.GithubOwner
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.exceptions.InvalidGithubRepositoryException
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator as JakartaValidator
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class GithubRepositoryValidatorTest {
    @Mock
    private lateinit var jakartaValidator: JakartaValidator

    @InjectMocks
    private lateinit var validator: GithubRepositoryValidator

    private val validRepo = GithubRepositoryResponse(
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
    )

    private fun violationWithMessage(message: String): ConstraintViolation<GithubRepositoryResponse> {
        val violation: ConstraintViolation<GithubRepositoryResponse> = mock()
        whenever(violation.message).thenReturn(message)
        return violation
    }

    @Test
    @DisplayName("validate bien, repo válido no lanza excepción")
    fun validateOk() {
        whenever(jakartaValidator.validate(validRepo)).thenReturn(emptySet())

        assertDoesNotThrow {
            validator.validate(validRepo)
        }

        verify(jakartaValidator, times(1)).validate(validRepo)
    }

    @Test
    @DisplayName("validate mal, nombre en blanco lanza InvalidGithubRepositoryException")
    fun validateBlankName() {
        val repo = validRepo.copy(name = "")
        val violation = violationWithMessage("must not be blank")
        whenever(jakartaValidator.validate(repo)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidGithubRepositoryException> {
            validator.validate(repo)
        }

        assertEquals("must not be blank", exception.message)
        verify(jakartaValidator, times(1)).validate(repo)
    }

    @Test
    @DisplayName("validate mal, url no github lanza InvalidGithubRepositoryException")
    fun validateBadUrl() {
        val repo = validRepo.copy(htmlUrl = "https://example.com/demo")
        val violation = violationWithMessage("must match \"https://github\\.com/.*\"")
        whenever(jakartaValidator.validate(repo)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidGithubRepositoryException> {
            validator.validate(repo)
        }

        assertEquals("must match \"https://github\\.com/.*\"", exception.message)
        verify(jakartaValidator, times(1)).validate(repo)
    }

    @Test
    @DisplayName("validate mal, id inválido lanza InvalidGithubRepositoryException")
    fun validateBadId() {
        val repo = validRepo.copy(id = -1L)
        val violation = violationWithMessage("must be greater than 0")
        whenever(jakartaValidator.validate(repo)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidGithubRepositoryException> {
            validator.validate(repo)
        }

        assertEquals("must be greater than 0", exception.message)
        verify(jakartaValidator, times(1)).validate(repo)
    }

    @Test
    @DisplayName("validate mal, múltiples violaciones concatena mensajes")
    fun validateMultipleViolations() {
        val repo = validRepo.copy(name = "", htmlUrl = "https://example.com/demo")
        val violation1 = violationWithMessage("must not be blank")
        val violation2 = violationWithMessage("must match \"https://github\\.com/.*\"")
        whenever(jakartaValidator.validate(repo)).thenReturn(setOf(violation1, violation2))

        val exception = assertThrows<InvalidGithubRepositoryException> {
            validator.validate(repo)
        }

        assertEquals(
            "must not be blank, must match \"https://github\\.com/.*\"",
            exception.message
        )
        verify(jakartaValidator, times(1)).validate(repo)
    }
}
