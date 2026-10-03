package dev.aragorn.portafolioapi.experience.model

import jakarta.persistence.EntityListeners
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener

class ExperienceTest {
    private val experience1 = Experience(
        id = "acme",
        title = "Backend Developer",
        company = "Acme",
        location = "Madrid",
        markdown = "# Backend Developer",
        contentHash = null
    )

    @Test
    @DisplayName("valores por defecto, sin imágenes ni fecha de sincronización")
    fun defaults() {
        assertTrue(experience1.images.isEmpty())
        assertNull(experience1.syncedAt)
    }

    @Test
    @DisplayName("usa AuditingEntityListener, si no syncedAt nunca se rellena")
    fun auditingListener() {
        val listeners = Experience::class.java.getAnnotation(EntityListeners::class.java)

        assertTrue(listeners.value.contains(AuditingEntityListener::class))
    }

    @Test
    @DisplayName("syncedAt está marcado con @LastModifiedDate")
    fun syncedAtLastModified() {
        val field = Experience::class.java.getDeclaredField("syncedAt")

        assertTrue(field.isAnnotationPresent(LastModifiedDate::class.java))
    }

    @Test
    @DisplayName("hashCode y toString con imágenes enlazadas no entran en bucle")
    fun hashCodeWithImages() {
        val experience = experience1.copy()
        experience.images.add(
            ExperienceImage(
                experience = experience,
                filename = "logo.png",
                relativePath = "acme/logo.png",
                cloudinaryPublicId = "logo-01JTEST",
                cloudinaryUrl = "https://cdn.test/logo.png",
                contentHash = "hash-logo"
            )
        )

        assertDoesNotThrow {
            experience.hashCode()
            experience.toString()
            experience.images[0].hashCode()
            experience.images[0].toString()
        }
    }

    @Test
    @DisplayName("equality sin imágenes enlazadas")
    fun equality() {
        val copy = experience1.copy()

        assertEquals(experience1, copy)
        assertEquals(experience1.hashCode(), copy.hashCode())
    }
}
