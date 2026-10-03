package dev.aragorn.portafolioapi.experience.validation

import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.dto.StorageImage
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceValidationException
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@ExtendWith(MockitoExtension::class)
class ExperienceValidatorTest {
    @Mock
    private lateinit var jakartaValidator: JakartaValidator

    @InjectMocks
    private lateinit var validator: ExperienceValidator

    private val validDto = ExperienceStorageDto(
        id = "acme",
        title = "Backend Developer",
        company = "Acme",
        location = "Madrid",
        mdFile = "# Backend Developer",
        images = listOf(StorageImage("acme/logo.png", File("logo.png"), "hash-logo"))
    )
    private val validDto2 = validDto.copy(id = "globex", company = "Globex")

    private fun violationWithMessage(message: String): ConstraintViolation<ExperienceStorageDto> {
        val violation: ConstraintViolation<ExperienceStorageDto> = mock()
        whenever(violation.message).thenReturn(message)
        return violation
    }

    @Test
    @DisplayName("validate bien, dto válido no lanza excepción")
    fun validateOk() {
        whenever(jakartaValidator.validate(validDto)).thenReturn(emptySet())

        assertDoesNotThrow {
            validator.validate(validDto)
        }

        verify(jakartaValidator, times(1)).validate(validDto)
    }

    @Test
    @DisplayName("validate mal, titulo en blanco lanza ExperienceValidationException")
    fun validateBlankTitle() {
        val dto = validDto.copy(title = "")
        val violation = violationWithMessage("must not be blank")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation))

        val exception = assertThrows<ExperienceValidationException> {
            validator.validate(dto)
        }

        assertEquals("must not be blank", exception.message)
        verify(jakartaValidator, times(1)).validate(dto)
    }

    @Test
    @DisplayName("validate mal, múltiples violaciones concatena mensajes")
    fun validateMultipleViolations() {
        val dto = validDto.copy(title = "", images = emptyList())
        val violation1 = violationWithMessage("must not be blank")
        val violation2 = violationWithMessage("must not be empty")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation1, violation2))

        val exception = assertThrows<ExperienceValidationException> {
            validator.validate(dto)
        }

        assertEquals("must not be blank, must not be empty", exception.message)
        verify(jakartaValidator, times(1)).validate(dto)
    }

    @Test
    @DisplayName("validates bien, lista válida no lanza excepción")
    fun validatesOk() {
        whenever(jakartaValidator.validate(validDto)).thenReturn(emptySet())
        whenever(jakartaValidator.validate(validDto2)).thenReturn(emptySet())

        assertDoesNotThrow {
            validator.validates(listOf(validDto, validDto2))
        }

        verify(jakartaValidator, times(1)).validate(validDto)
        verify(jakartaValidator, times(1)).validate(validDto2)
    }

    @Test
    @DisplayName("validates bien, lista vacía no lanza excepción")
    fun validatesEmpty() {
        assertDoesNotThrow {
            validator.validates(emptyList())
        }

        verify(jakartaValidator, times(0)).validate(any<ExperienceStorageDto>())
    }

    @Test
    @DisplayName("validates mal, concatena las violaciones de todos los elementos")
    fun validatesWithViolations() {
        val dto1 = validDto.copy(title = "")
        val dto2 = validDto2.copy(company = "")
        val violation1 = violationWithMessage("title must not be blank")
        val violation2 = violationWithMessage("company must not be blank")
        whenever(jakartaValidator.validate(dto1)).thenReturn(setOf(violation1))
        whenever(jakartaValidator.validate(dto2)).thenReturn(setOf(violation2))

        val exception = assertThrows<ExperienceValidationException> {
            validator.validates(listOf(dto1, dto2))
        }

        assertEquals("title must not be blank, company must not be blank", exception.message)
        verify(jakartaValidator, times(1)).validate(dto1)
        verify(jakartaValidator, times(1)).validate(dto2)
    }
}
