package dev.aragorn.portafolioapi.certificates.validator

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
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
class CertificateValidatorTest {
    @Mock
    private lateinit var jakartaValidator: JakartaValidator

    @InjectMocks
    private lateinit var validator: CertificateValidator

    private val validDto = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        "2025-03-15"
    )

    private fun violationWithMessage(message: String): ConstraintViolation<CertificatesResponseDto> {
        val violation: ConstraintViolation<CertificatesResponseDto> = mock()
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
    @DisplayName("validate mal, titulo en blanco lanza InvalidCertificateException")
    fun validateBlankTitle() {
        val dto = validDto.copy(titulo = "")
        val violation = violationWithMessage("must not be blank")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidCertificateException> {
            validator.validate(dto)
        }

        assertEquals("must not be blank", exception.message)
        verify(jakartaValidator, times(1)).validate(dto)
    }

    @Test
    @DisplayName("validate mal, url no drive lanza InvalidCertificateException")
    fun validateBadUrl() {
        val dto = validDto.copy(url = "https://example.com/certificado")
        val violation = violationWithMessage("must match \"https://drive\\.google\\.com/.*\"")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidCertificateException> {
            validator.validate(dto)
        }

        assertEquals("must match \"https://drive\\.google\\.com/.*\"", exception.message)
        verify(jakartaValidator, times(1)).validate(dto)
    }

    @Test
    @DisplayName("validate mal, fecha con formato inválido lanza InvalidCertificateException")
    fun validateBadDate() {
        val dto = validDto.copy(fecha = "buenasnoches")
        val violation = violationWithMessage("must match \"\\d{4}-\\d{2}-\\d{2}\"")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation))

        val exception = assertThrows<InvalidCertificateException> {
            validator.validate(dto)
        }

        assertEquals("must match \"\\d{4}-\\d{2}-\\d{2}\"", exception.message)
        verify(jakartaValidator, times(1)).validate(dto)
    }

    @Test
    @DisplayName("validate mal, múltiples violaciones concatena mensajes")
    fun validateMultipleViolations() {
        val dto = CertificatesResponseDto("", "https://example.com/certificado", "buenasnoches")
        val violation1 = violationWithMessage("must not be blank")
        val violation2 = violationWithMessage("must match \"https://drive\\.google\\.com/.*\"")
        whenever(jakartaValidator.validate(dto)).thenReturn(setOf(violation1, violation2))

        val exception = assertThrows<InvalidCertificateException> {
            validator.validate(dto)
        }

        assertEquals(
            "must not be blank, must match \"https://drive\\.google\\.com/.*\"",
            exception.message
        )
        verify(jakartaValidator, times(1)).validate(dto)
    }
}
