package dev.aragorn.portafolioapi.projects.dto

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ProjectResponseDtoTest {
    private val dto1 = ProjectResponseDto(
        name = "demo",
        description = "demo repo",
        url = "https://github.com/Aragorn7372/demo",
        pagesUrl = "https://aragorn7372.github.io/demo/",
        owner = "Aragorn7372",
        avatarUrl = "https://avatars.github.com/u/1",
        stars = 12,
        forks = 4,
        commits = 342,
        languages = mapOf("Kotlin" to 100.0),
        topics = listOf("kotlin"),
    )

    @Test
    @DisplayName("technologies por defecto vacío")
    fun defaultTechnologies() {
        assertEquals(emptyList<String>(), dto1.technologies)
    }

    @Test
    fun equality() {
        val copy = dto1.copy()

        assertEquals(dto1, copy)
        assertEquals(dto1.hashCode(), copy.hashCode())
    }
}
