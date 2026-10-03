package dev.aragorn.portafolioapi.experience.service

import dev.aragorn.portafolioapi.common.images.CloudinaryService
import dev.aragorn.portafolioapi.experience.client.ExperienceClient
import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.dto.StorageImage
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceNotFoundException
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceValidationException
import dev.aragorn.portafolioapi.experience.exceptions.StorageExperienceException
import dev.aragorn.portafolioapi.experience.mapper.ExperienceMapper
import dev.aragorn.portafolioapi.experience.model.Experience
import dev.aragorn.portafolioapi.experience.model.ExperienceImage
import dev.aragorn.portafolioapi.experience.repository.ExperienceRepository
import dev.aragorn.portafolioapi.experience.storage.StorageExperienZipImpl
import dev.aragorn.portafolioapi.experience.validation.ExperienceValidator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class ExperienceServiceImplTest {
    @Mock
    private lateinit var repository: ExperienceRepository
    @Mock
    private lateinit var mapper: ExperienceMapper
    @Mock
    private lateinit var client: ExperienceClient
    @Mock
    private lateinit var storage: StorageExperienZipImpl
    @Mock
    private lateinit var validator: ExperienceValidator
    @Mock
    private lateinit var cloudinary: CloudinaryService
    @InjectMocks
    private lateinit var service: ExperienceServiceImpl

    @TempDir
    lateinit var tempDir: Path

    private fun tempFile(name: String): File =
        tempDir.resolve(name).toFile().apply { writeText(name) }

    private fun zip(): Path = tempFile("experience.zip").toPath()

    private fun experience(
        id: String = "acme",
        title: String = "Backend Developer",
        company: String = "Acme",
        location: String = "Madrid",
        markdown: String = "# Backend Developer"
    ) = Experience(
        id = id,
        title = title,
        company = company,
        location = location,
        markdown = markdown,
        contentHash = null
    )

    private fun Experience.withImage(path: String, hash: String): Experience {
        val name = path.substringAfterLast("/")
        images.add(
            ExperienceImage(
                experience = this,
                filename = name,
                relativePath = path,
                cloudinaryPublicId = "${name.substringBeforeLast(".")}-01JOLD",
                cloudinaryUrl = "https://cdn.test/old/$name",
                contentHash = hash
            )
        )
        return this
    }

    private fun storageDto(
        images: List<StorageImage>,
        id: String = "acme",
        title: String = "Backend Developer",
        company: String = "Acme",
        location: String = "Madrid",
        mdFile: String = "# Backend Developer"
    ) = ExperienceStorageDto(
        id = id,
        title = title,
        company = company,
        location = location,
        mdFile = mdFile,
        images = images
    )

    @Test
    @DisplayName("refresh bien, todas nuevas ninguna en base de datos")
    fun refresh() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val remote = storageDto(listOf(logo))
        val nueva = experience()
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf<Experience>())
        whenever(mapper.experienceStorageDtotoModel(remote)).thenReturn(nueva)
        whenever(cloudinary.upload(eq(logo.file), any())).thenReturn("https://cdn.test/logo.png")

        service.refresh()

        verify(client, times(1)).getExperience()
        verify(storage, times(1)).readZip(zip)
        verify(validator, times(1)).validates(listOf(remote))
        verify(mapper, times(1)).experienceStorageDtotoModel(remote)
        verify(cloudinary, times(1)).upload(eq(logo.file), any())
        verify(cloudinary, times(0)).delete(any())
        verify(repository, times(0)).delete(any())

        val captor = argumentCaptor<Experience>()
        verify(repository, times(1)).save(captor.capture())
        val saved = captor.firstValue
        assertSame(nueva, saved)
        assertEquals(1, saved.images.size)
        val image = saved.images[0]
        assertSame(nueva, image.experience)
        assertEquals("logo.png", image.filename)
        assertEquals("acme/logo.png", image.relativePath)
        assertEquals("https://cdn.test/logo.png", image.cloudinaryUrl)
        assertEquals("hash-logo", image.contentHash)
        assertTrue(image.cloudinaryPublicId.startsWith("logo-"))

        assertFalse(Files.exists(zip), "el zip debe borrarse")
        assertFalse(logo.file.exists(), "las imágenes temporales deben borrarse")
    }

    @Test
    @DisplayName("refresh bien, experiencia existente actualiza sus campos sin subir imágenes")
    fun refreshUpdateFields() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val remote = storageDto(
            listOf(logo),
            title = "Senior Backend Developer",
            company = "Acme Corp",
            location = "Remoto",
            mdFile = "# Senior"
        )
        val actual = experience().withImage("acme/logo.png", "hash-logo")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf(actual))

        service.refresh()

        assertEquals("Senior Backend Developer", actual.title)
        assertEquals("Acme Corp", actual.company)
        assertEquals("Remoto", actual.location)
        assertEquals("# Senior", actual.markdown)
        assertEquals(1, actual.images.size)
        verify(mapper, times(0)).experienceStorageDtotoModel(any())
        verify(cloudinary, times(0)).upload(any(), any())
        verify(cloudinary, times(0)).delete(any())
        verify(repository, times(1)).save(actual)
        verify(repository, times(0)).delete(any())
        assertFalse(Files.exists(zip))
    }

    @Test
    @DisplayName("refresh bien, imagen nueva en experiencia existente se sube")
    fun refreshNewImage() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val foto = StorageImage("acme/foto.jpg", tempFile("foto.jpg"), "hash-foto")
        val remote = storageDto(listOf(logo, foto))
        val actual = experience().withImage("acme/logo.png", "hash-logo")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf(actual))
        whenever(cloudinary.upload(eq(foto.file), any())).thenReturn("https://cdn.test/foto.jpg")

        service.refresh()

        verify(cloudinary, times(1)).upload(any(), any())
        verify(cloudinary, times(1)).upload(eq(foto.file), any())
        assertEquals(listOf("acme/logo.png", "acme/foto.jpg"), actual.images.map { it.relativePath })
        val nueva = actual.images.first { it.relativePath == "acme/foto.jpg" }
        assertEquals("https://cdn.test/foto.jpg", nueva.cloudinaryUrl)
        assertEquals("hash-foto", nueva.contentHash)
        assertTrue(nueva.cloudinaryPublicId.startsWith("foto-"))
        verify(repository, times(1)).save(actual)
        assertFalse(logo.file.exists())
        assertFalse(foto.file.exists())
    }

    @Test
    @DisplayName("refresh bien, imagen modificada se resube con el mismo public id")
    fun refreshModifiedImage() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-nuevo")
        val remote = storageDto(listOf(logo))
        val actual = experience().withImage("acme/logo.png", "hash-viejo")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf(actual))
        whenever(cloudinary.upload(logo.file, "logo-01JOLD")).thenReturn("https://cdn.test/v2/logo.png")

        service.refresh()

        verify(cloudinary, times(1)).upload(logo.file, "logo-01JOLD")
        assertEquals(1, actual.images.size)
        assertEquals("https://cdn.test/v2/logo.png", actual.images[0].cloudinaryUrl)
        assertEquals("hash-nuevo", actual.images[0].contentHash)
        assertEquals("logo-01JOLD", actual.images[0].cloudinaryPublicId)
        verify(repository, times(1)).save(actual)
    }

    @Test
    @DisplayName("refresh bien, imagen eliminada en remoto se borra de cloudinary y de la experiencia")
    fun refreshDeletedImage() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val remote = storageDto(listOf(logo))
        val actual = experience()
            .withImage("acme/logo.png", "hash-logo")
            .withImage("acme/foto.jpg", "hash-foto")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf(actual))

        service.refresh()

        verify(cloudinary, times(1)).delete("foto-01JOLD")
        verify(cloudinary, times(0)).delete("logo-01JOLD")
        verify(cloudinary, times(0)).upload(any(), any())
        assertEquals(listOf("acme/logo.png"), actual.images.map { it.relativePath })
        verify(repository, times(1)).save(actual)
    }

    @Test
    @DisplayName("refresh bien, experiencia eliminada en remoto se borra con sus imágenes")
    fun refreshDeletedExperience() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val remote = storageDto(listOf(logo))
        val actual = experience().withImage("acme/logo.png", "hash-logo")
        val borrada = experience(id = "globex", company = "Globex")
            .withImage("globex/banner.png", "hash-banner")
            .withImage("globex/equipo.jpg", "hash-equipo")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(repository.findAll()).thenReturn(mutableListOf(actual, borrada))

        service.refresh()

        verify(cloudinary, times(1)).delete("banner-01JOLD")
        verify(cloudinary, times(1)).delete("equipo-01JOLD")
        verify(repository, times(1)).delete(borrada)
        verify(repository, times(0)).delete(actual)
        verify(repository, times(1)).save(actual)
    }

    @Test
    @DisplayName("refresh mal, validación falla, no guarda y limpia temporales")
    fun refreshValidationError() = runTest {
        val zip = zip()
        val logo = StorageImage("acme/logo.png", tempFile("logo.png"), "hash-logo")
        val remote = storageDto(listOf(logo), title = "")
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenReturn(listOf(remote))
        whenever(validator.validates(listOf(remote)))
            .thenThrow(ExperienceValidationException("must not be blank"))

        val exception = assertThrows<ExperienceValidationException> {
            service.refresh()
        }

        assertEquals("must not be blank", exception.message)
        verify(repository, times(0)).findAll()
        verify(repository, times(0)).save(any<Experience>())
        verify(cloudinary, times(0)).upload(any(), any())
        assertFalse(Files.exists(zip), "el zip debe borrarse")
        assertFalse(logo.file.exists(), "las imágenes temporales deben borrarse")
    }

    @Test
    @DisplayName("refresh mal, readZip falla y borra el zip")
    fun refreshReadZipError() = runTest {
        val zip = zip()
        whenever(client.getExperience()).thenReturn(zip)
        whenever(storage.readZip(zip)).thenThrow(StorageExperienceException("No se encontro experience.json"))

        val exception = assertThrows<StorageExperienceException> {
            service.refresh()
        }

        assertEquals("No se encontro experience.json", exception.message)
        verify(validator, times(0)).validates(any())
        verify(repository, times(0)).findAll()
        assertFalse(Files.exists(zip), "el zip debe borrarse")
    }

    @Test
    @DisplayName("refresh mal, el cliente falla y no se lee nada")
    fun refreshClientError() = runTest {
        whenever(client.getExperience()).thenThrow(RuntimeException("No se pudo cargar el zip"))

        val exception = assertThrows<RuntimeException> {
            service.refresh()
        }

        assertEquals("No se pudo cargar el zip", exception.message)
        verify(storage, times(0)).readZip(any())
        verify(repository, times(0)).findAll()
    }

    @Test
    @DisplayName("obtener todas las experiencias")
    fun getExperiences() = runTest {
        val experience1 = experience()
        val experience2 = experience(id = "globex", company = "Globex")
        val dto1 = ExperienceResponseDto("acme", "Backend Developer", "Acme", "Madrid", null)
        val dto2 = ExperienceResponseDto("globex", "Backend Developer", "Globex", "Madrid", null)
        whenever(repository.findAll()).thenReturn(mutableListOf(experience1, experience2))
        whenever(mapper.experienceToDto(experience1)).thenReturn(dto1)
        whenever(mapper.experienceToDto(experience2)).thenReturn(dto2)

        val result = service.getExperiences()

        assertEquals(listOf(dto1, dto2), result)
        verify(repository, times(1)).findAll()
        verify(mapper, times(1)).experienceToDto(experience1)
        verify(mapper, times(1)).experienceToDto(experience2)
    }

    @Test
    @DisplayName("obtener experiencia por id")
    fun getExperience() = runTest {
        val experience1 = experience()
        val dto = ExperienceDetailsResponseDto(
            "acme", "Backend Developer", "Acme", "Madrid", "# Backend Developer", emptyList()
        )
        whenever(repository.findExperienceById("acme")).thenReturn(experience1)
        whenever(mapper.experienceToDetailsDto(experience1)).thenReturn(dto)

        val result = service.getExperience("acme")

        assertEquals(dto, result)
        verify(repository, times(1)).findExperienceById("acme")
        verify(mapper, times(1)).experienceToDetailsDto(experience1)
    }

    @Test
    @DisplayName("obtener experiencia por id mal, no existe lanza ExperienceNotFoundException")
    fun getExperienceNotFound() = runTest {
        whenever(repository.findExperienceById("no-existe")).thenReturn(null)

        val exception = assertThrows<ExperienceNotFoundException> {
            service.getExperience("no-existe")
        }

        assertEquals("Experience not found", exception.message)
        verify(mapper, times(0)).experienceToDetailsDto(any())
    }
}
