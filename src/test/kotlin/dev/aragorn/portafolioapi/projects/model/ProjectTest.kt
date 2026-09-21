package dev.aragorn.portafolioapi.projects.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull

class ProjectTest {
    private val owner1 = Owner(id = 1, name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1")
    private val project1 = Project(
        id = 123L,
        name = "demo",
        fullName = "Aragorn7372/demo",
        description = "demo repo",
        url = "https://github.com/Aragorn7372/demo",
        owner = owner1,
        stars = 12,
        forks = 4,
        commits = 342,
        languages = mapOf("Kotlin" to 100.0),
    )

    @Test
    @DisplayName("defaults de persistencia")
    fun defaults() {
        assertFalse(project1.fork)
        assertNull(project1.pagesUrl)
        assertEquals(emptyList<String>(), project1.topics)
        assertNull(project1.createdAt)
        assertNull(project1.updatedAt)
        assertNull(project1.pushedAt)
        assertEquals(emptyList<String>(), project1.technologies)
    }

    @Test
    fun equality() {
        val copy = project1.copy()

        assertEquals(project1, copy)
        assertEquals(project1.hashCode(), copy.hashCode())
    }
}
