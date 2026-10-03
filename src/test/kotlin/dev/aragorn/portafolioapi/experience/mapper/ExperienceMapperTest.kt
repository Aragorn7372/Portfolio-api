package dev.aragorn.portafolioapi.experience.mapper

import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceImageResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.dto.StorageImage
import dev.aragorn.portafolioapi.experience.model.Experience
import dev.aragorn.portafolioapi.experience.model.ExperienceImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class ExperienceMapperTest {
    private val mapper = ExperienceMapper()

    private fun experience() = Experience(
        id = "acme",
        title = "Backend Developer",
        company = "Acme",
        location = "Madrid",
        markdown = "# Backend Developer",
        contentHash = null
    )

    private fun image(experience: Experience, path: String, url: String) = ExperienceImage(
        experience = experience,
        filename = path.substringAfterLast("/"),
        relativePath = path,
        cloudinaryPublicId = path.substringAfterLast("/").substringBeforeLast(".") + "-01JTEST",
        cloudinaryUrl = url,
        contentHash = "hash-$path"
    )

    private val storageDto = ExperienceStorageDto(
        id = "acme",
        title = "Backend Developer",
        company = "Acme",
        location = "Madrid",
        mdFile = "# Backend Developer",
        images = listOf(StorageImage("acme/logo.png", File("logo.png"), "hash-logo"))
    )

    @Test
    @DisplayName("experienceToDto con logo.png usa su url como companyLogo")
    fun experienceToDto() {
        val experience = experience()
        experience.images.add(image(experience, "acme/foto.jpg", "https://cdn.test/foto.jpg"))
        experience.images.add(image(experience, "acme/logo.png", "https://cdn.test/logo.png"))

        val result = mapper.experienceToDto(experience)

        assertEquals(
            ExperienceResponseDto(
                id = "acme",
                title = "Backend Developer",
                company = "Acme",
                location = "Madrid",
                companyLogo = "https://cdn.test/logo.png"
            ),
            result
        )
    }

    @Test
    @DisplayName("experienceToDto sin logo.png deja companyLogo a null")
    fun experienceToDtoWithoutLogo() {
        val experience = experience()
        experience.images.add(image(experience, "acme/foto.jpg", "https://cdn.test/foto.jpg"))
        experience.images.add(image(experience, "acme/logo-viejo.png", "https://cdn.test/logo-viejo.png"))

        val result = mapper.experienceToDto(experience)

        assertNull(result.companyLogo)
    }

    @Test
    @DisplayName("experienceToDetailsDto mapea markdown e imágenes")
    fun experienceToDetailsDto() {
        val experience = experience()
        experience.images.add(image(experience, "acme/logo.png", "https://cdn.test/logo.png"))
        experience.images.add(image(experience, "acme/foto.jpg", "https://cdn.test/foto.jpg"))

        val result = mapper.experienceToDetailsDto(experience)

        assertEquals(
            ExperienceDetailsResponseDto(
                id = "acme",
                title = "Backend Developer",
                company = "Acme",
                location = "Madrid",
                markdown = "# Backend Developer",
                images = listOf(
                    ExperienceImageResponseDto("logo.png", "https://cdn.test/logo.png"),
                    ExperienceImageResponseDto("foto.jpg", "https://cdn.test/foto.jpg")
                )
            ),
            result
        )
    }

    @Test
    @DisplayName("experienceStorageDtotoModel copia campos, sin hash ni imágenes")
    fun experienceStorageDtotoModel() {
        val result = mapper.experienceStorageDtotoModel(storageDto)

        assertEquals(experience(), result)
        assertNull(result.contentHash)
        assertTrue(result.images.isEmpty())
    }
}
