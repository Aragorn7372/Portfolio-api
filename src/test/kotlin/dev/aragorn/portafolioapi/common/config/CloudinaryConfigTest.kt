package dev.aragorn.portafolioapi.common.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class CloudinaryConfigTest {

    @Test
    @DisplayName("cloudinary bien, construye el cliente a partir de la url")
    fun cloudinary() {
        val config = CloudinaryConfig()
        ReflectionTestUtils.setField(config, "cloudinaryUrl", "cloudinary://test-key:test-secret@test-cloud")

        val result = config.cloudinary()

        assertEquals("test-cloud", result.config.cloudName)
        assertEquals("test-key", result.config.apiKey)
        assertEquals("test-secret", result.config.apiSecret)
    }
}
