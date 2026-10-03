package dev.aragorn.portafolioapi.experience.exceptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExperiencesExceptionsTest {

    @Test
    @DisplayName("todas las excepciones de experience son ExperiencesExceptions e IllegalArgumentException")
    fun hierarchy() {
        val exceptions = listOf(
            StorageExperienceException("storage"),
            ExperienceNotFoundException("not found"),
            ExperienceValidationException("validation")
        )

        exceptions.forEach {
            assertTrue(it is ExperiencesExceptions)
            assertTrue(it is IllegalArgumentException)
        }
        assertEquals(listOf("storage", "not found", "validation"), exceptions.map { it.message })
    }
}
