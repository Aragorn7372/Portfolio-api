package dev.aragorn.portafolioapi.certificates.dto

import jakarta.validation.Validation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class CertificatesValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val dto1 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        "2025-03-15"
    )

    @Test
    fun valid() {
        assertTrue(validator.validate(dto1).isEmpty())
    }

    @Test
    @DisplayName("titulo en blanco viola titulo")
    fun blankTitle() {
        val violations = validator.validate(dto1.copy(titulo = ""))

        assertTrue(violations.any { it.propertyPath.toString() == "titulo" })
    }

    @Test
    @DisplayName("url no drive viola url")
    fun badUrl() {
        val violations = validator.validate(dto1.copy(url = "https://example.com/certificado"))

        assertTrue(violations.any { it.propertyPath.toString() == "url" })
    }

    @Test
    @DisplayName("fecha con formato inválido viola fecha")
    fun badDate() {
        val violations = validator.validate(dto1.copy(fecha = "buenasnoches"))

        assertTrue(violations.any { it.propertyPath.toString() == "fecha" })
    }
}
