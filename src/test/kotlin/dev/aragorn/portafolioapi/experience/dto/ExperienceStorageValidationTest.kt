package dev.aragorn.portafolioapi.experience.dto

import jakarta.validation.Validation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

class ExperienceStorageValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val dto1 = ExperienceStorageDto(
        id = "acme",
        title = "Backend Developer",
        company = "Acme",
        location = "Madrid",
        mdFile = "# Backend Developer",
        images = listOf(StorageImage("acme/logo.png", File("logo.png"), "hash-logo"))
    )

    @Test
    fun valid() {
        assertTrue(validator.validate(dto1).isEmpty())
    }

    @Test
    @DisplayName("id en blanco viola id")
    fun blankId() {
        val violations = validator.validate(dto1.copy(id = " "))

        assertTrue(violations.any { it.propertyPath.toString() == "id" })
    }

    @Test
    @DisplayName("title de más de 200 caracteres viola title")
    fun longTitle() {
        val violations = validator.validate(dto1.copy(title = "a".repeat(201)))

        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    @DisplayName("company en blanco viola company")
    fun blankCompany() {
        val violations = validator.validate(dto1.copy(company = ""))

        assertTrue(violations.any { it.propertyPath.toString() == "company" })
    }

    @Test
    @DisplayName("location en blanco viola location")
    fun blankLocation() {
        val violations = validator.validate(dto1.copy(location = ""))

        assertTrue(violations.any { it.propertyPath.toString() == "location" })
    }

    @Test
    @DisplayName("mdFile en blanco viola mdFile")
    fun blankMdFile() {
        val violations = validator.validate(dto1.copy(mdFile = ""))

        assertTrue(violations.any { it.propertyPath.toString() == "mdFile" })
    }

    @Test
    @DisplayName("images vacía viola images")
    fun emptyImages() {
        val violations = validator.validate(dto1.copy(images = emptyList()))

        assertTrue(violations.any { it.propertyPath.toString() == "images" })
    }
}
