package dev.aragorn.portafolioapi.certificates.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CertificatesTest {
    private val date = LocalDate.of(2025, 3, 15)
    private val url1 = "https://drive.google.com/file/d/kotlin-developer-123/view"
    private val certificate1 = Certificates(
        name = "Kotlin Developer",
        date = date,
        url = url1
    )

    @Test
    @DisplayName("extrae el id de la url de drive")
    fun extractId() {
        assertEquals("kotlin-developer-123", certificate1.id)
    }

    @Test
    @DisplayName("url con query extrae el mismo id")
    fun extractIdWithQuery() {
        val certificate = Certificates("Kotlin Developer", date, "$url1?usp=drivesdk")

        assertEquals("kotlin-developer-123", certificate.id)
    }

    @Test
    @DisplayName("url no drive lanza IllegalArgumentException")
    fun invalidUrl() {
        val exception = assertThrows<IllegalArgumentException> {
            Certificates("Kotlin Developer", date, "https://example.com/certificado")
        }

        assertEquals("Invalid url: https://example.com/certificado", exception.message)
    }

    @Test
    fun equality() {
        val copy = certificate1.copy()

        assertEquals(certificate1, copy)
        assertEquals(certificate1.hashCode(), copy.hashCode())
    }
}
