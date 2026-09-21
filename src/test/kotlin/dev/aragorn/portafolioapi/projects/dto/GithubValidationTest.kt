package dev.aragorn.portafolioapi.projects.dto

import jakarta.validation.Validation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class GithubValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val repo1 = GithubRepositoryResponse(
        id = 123L,
        name = "demo",
        fullName = "Aragorn7372/demo",
        htmlUrl = "https://github.com/Aragorn7372/demo",
        description = "demo repo",
        owner = GithubOwner("Aragorn7372", "https://avatars.github.com/u/1"),
        language = "Kotlin",
        stargazersCount = 12,
        forksCount = 4,
        topics = listOf("kotlin"),
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-06-01T00:00:00Z",
        pushedAt = null,
    )

    @Test
    fun valid() {
        assertTrue(validator.validate(repo1).isEmpty())
    }

    @Test
    @DisplayName("id no positivo viola id")
    fun badId() {
        val violations = validator.validate(repo1.copy(id = 0L))

        assertTrue(violations.any { it.propertyPath.toString() == "id" })
    }

    @Test
    @DisplayName("nombre en blanco viola name")
    fun blankName() {
        val violations = validator.validate(repo1.copy(name = ""))

        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    @DisplayName("url no github viola htmlUrl")
    fun badUrl() {
        val violations = validator.validate(repo1.copy(htmlUrl = "https://example.com/demo"))

        assertTrue(violations.any { it.propertyPath.toString() == "htmlUrl" })
    }

    @Test
    @DisplayName("owner en blanco viola en cascada")
    fun blankOwner() {
        val violations = validator.validate(repo1.copy(owner = GithubOwner("", "")))

        assertTrue(violations.any { it.propertyPath.toString() == "owner.login" })
    }

    @Test
    @DisplayName("contadores negativos violan")
    fun negativeCounts() {
        val violations = validator.validate(repo1.copy(stargazersCount = -1))

        assertTrue(violations.any { it.propertyPath.toString() == "stargazersCount" })
    }
}
