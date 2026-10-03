package dev.aragorn.portafolioapi.experience.storage

import dev.aragorn.portafolioapi.experience.exceptions.StorageExperienceException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StorageExperienZipImplTest {
    private val storage = StorageExperienZipImpl()

    @TempDir
    lateinit var tempDir: Path

    private val logoBytes = "logo-bytes".toByteArray()
    private val fotoBytes = "foto-bytes".toByteArray()
    private val markdown = "# Backend Developer\n\nTrabajo con Kotlin y Spring."

    private val json = """
        [
          {
            "id": "acme",
            "title": "Backend Developer",
            "company": "Acme",
            "location": "Madrid",
            "md_file": "acme/acme.md",
            "images": ["acme/logo.png", "acme/foto.jpg"]
          }
        ]
    """.trimIndent()

    private fun buildZip(entries: Map<String, ByteArray>): Path {
        val zip = tempDir.resolve("experience.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zip
    }

    @Test
    @DisplayName("readZip bien, lee json, markdown e imágenes")
    fun readZip() = runTest {
        val zip = buildZip(
            mapOf(
                "experience.json" to json.toByteArray(),
                "acme/acme.md" to markdown.toByteArray(),
                "acme/logo.png" to logoBytes,
                "acme/foto.jpg" to fotoBytes
            )
        )

        val result = storage.readZip(zip)

        try {
            assertEquals(1, result.size)
            val experience = result[0]
            assertEquals("acme", experience.id)
            assertEquals("Backend Developer", experience.title)
            assertEquals("Acme", experience.company)
            assertEquals("Madrid", experience.location)
            assertEquals(markdown, experience.mdFile)
            assertEquals(listOf("acme/logo.png", "acme/foto.jpg"), experience.images.map { it.path })
            assertArrayEquals(logoBytes, experience.images[0].file.readBytes())
            assertArrayEquals(fotoBytes, experience.images[1].file.readBytes())
            assertEquals(storage.calculateHash(experience.images[0].file), experience.images[0].contentHash)
        } finally {
            result.flatMap { it.images }.forEach { it.file.delete() }
        }
    }

    @Test
    @DisplayName("readZip mal, zip inexistente lanza StorageExperienceException")
    fun readZipNotExists() = runTest {
        val exception = assertThrows<StorageExperienceException> {
            storage.readZip(tempDir.resolve("no-existe.zip"))
        }

        assertEquals("El ZIP no existe o no se puede leer", exception.message)
    }

    @Test
    @DisplayName("readZip mal, directorio en vez de fichero lanza StorageExperienceException")
    fun readZipDirectory() = runTest {
        val exception = assertThrows<StorageExperienceException> {
            storage.readZip(tempDir)
        }

        assertEquals("El ZIP no existe o no se puede leer", exception.message)
    }

    @Test
    @DisplayName("readZip mal, sin experience.json lanza StorageExperienceException")
    fun readZipWithoutJson() = runTest {
        val zip = buildZip(mapOf("acme/acme.md" to markdown.toByteArray()))

        val exception = assertThrows<StorageExperienceException> {
            storage.readZip(zip)
        }

        assertEquals("No se encontro experience.json", exception.message)
    }

    @Test
    @DisplayName("readZip bien, zipball de GitHub con carpeta raíz")
    fun readZipWithRootFolder() = runTest {
        val root = "test-user-test-repo-abc123"
        val zip = buildZip(
            mapOf(
                "$root/experience.json" to json.toByteArray(),
                "$root/acme/acme.md" to markdown.toByteArray(),
                "$root/acme/logo.png" to logoBytes,
                "$root/acme/foto.jpg" to fotoBytes
            )
        )

        val result = storage.readZip(zip)

        try {
            assertEquals(1, result.size)
            assertEquals("acme", result[0].id)
            assertEquals(markdown, result[0].mdFile)
            assertEquals(listOf("acme/logo.png", "acme/foto.jpg"), result[0].images.map { it.path })
            assertArrayEquals(logoBytes, result[0].images[0].file.readBytes())
            assertArrayEquals(fotoBytes, result[0].images[1].file.readBytes())
        } finally {
            result.flatMap { it.images }.forEach { it.file.delete() }
        }
    }

    @Test
    @DisplayName("readZip mal, markdown inexistente lanza StorageExperienceException")
    fun readZipWithoutMarkdown() = runTest {
        val zip = buildZip(
            mapOf(
                "experience.json" to json.toByteArray(),
                "acme/logo.png" to logoBytes,
                "acme/foto.jpg" to fotoBytes
            )
        )

        val exception = assertThrows<StorageExperienceException> {
            storage.readZip(zip)
        }

        assertEquals("no fue encontrado acme/acme.md", exception.message)
    }

    @Test
    @DisplayName("readZip mal, imagen inexistente lanza StorageExperienceException")
    fun readZipWithoutImage() = runTest {
        val zip = buildZip(
            mapOf(
                "experience.json" to json.toByteArray(),
                "acme/acme.md" to markdown.toByteArray(),
                "acme/logo.png" to logoBytes
            )
        )

        val exception = assertThrows<StorageExperienceException> {
            storage.readZip(zip)
        }

        assertEquals("No se encontró acme/foto.jpg", exception.message)
    }

    @Test
    @DisplayName("calculateHash bien, devuelve el SHA-256 en hexadecimal")
    fun calculateHash() {
        val file = tempDir.resolve("hola.txt").toFile()
        file.writeText("hola")

        val result = storage.calculateHash(file)

        assertEquals("b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79", result)
    }

    @Test
    @DisplayName("calculateHash, mismo contenido mismo hash y distinto contenido distinto hash")
    fun calculateHashCompare() {
        val file1 = tempDir.resolve("a.txt").toFile().apply { writeText("igual") }
        val file2 = tempDir.resolve("b.txt").toFile().apply { writeText("igual") }
        val file3 = tempDir.resolve("c.txt").toFile().apply { writeText("distinto") }

        assertEquals(storage.calculateHash(file1), storage.calculateHash(file2))
        assertTrue(storage.calculateHash(file1) != storage.calculateHash(file3))
    }
}
