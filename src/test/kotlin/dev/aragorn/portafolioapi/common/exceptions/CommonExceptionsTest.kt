package dev.aragorn.portafolioapi.common.exceptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CommonExceptionsTest {

    @Test
    @DisplayName("CloudinaryException es CommonExceptions e IllegalArgumentException")
    fun cloudinaryException() {
        val exception = CloudinaryException("fallo cloudinary")

        assertTrue(exception is CommonExceptions)
        assertTrue(exception is IllegalArgumentException)
        assertEquals("fallo cloudinary", exception.message)
    }
}
