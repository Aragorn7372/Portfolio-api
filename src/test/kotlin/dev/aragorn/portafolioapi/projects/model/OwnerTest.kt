package dev.aragorn.portafolioapi.projects.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class OwnerTest {
    private val owner1 = Owner(name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1")

    @Test
    @DisplayName("id por defecto cero")
    fun defaultId() {
        assertEquals(0L, owner1.id)
    }

    @Test
    fun equality() {
        val copy = owner1.copy()

        assertEquals(owner1, copy)
        assertEquals(owner1.hashCode(), copy.hashCode())
    }
}
