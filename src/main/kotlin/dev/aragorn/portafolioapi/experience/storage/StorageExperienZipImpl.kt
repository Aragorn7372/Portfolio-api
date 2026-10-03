package dev.aragorn.portafolioapi.experience.storage

import dev.aragorn.portafolioapi.experience.dto.ExperienceJsonDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.dto.StorageImage
import dev.aragorn.portafolioapi.experience.exceptions.StorageExperienceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.logging.Logger
import java.util.zip.ZipInputStream

@Service
class StorageExperienZipImpl: StorageExperienceZip {
    private val log: Logger = Logger.getLogger(StorageExperienZipImpl::class.java.name)

    override suspend fun readZip(zip: Path): List<ExperienceStorageDto> = withContext(Dispatchers.IO) {
        if (!Files.exists(zip) ||
            !Files.isRegularFile(zip) ||
            !Files.isReadable(zip)) {
                fail("El ZIP no existe o no se puede leer")
            }
        log.info("reading experience zip $zip")

        val jsonFile = findFile(zip, "experience.json")
            ?: fail("No se encontro experience.json")
        val json = try {
            jacksonObjectMapper().readValue<List<ExperienceJsonDto>>(jsonFile)
        } finally {
            jsonFile.delete()
        }
        log.info("experience.json contains ${json.size} experiences")

        return@withContext json.map {
            val mdTemp = findFile(zip, it.mdFile)
                ?: fail("no fue encontrado ${it.mdFile}")
            val mdContent = try {
                mdTemp.readText()
            } finally {
                mdTemp.delete()
            }
            ExperienceStorageDto(
                id = it.id,
                title = it.title,
                company = it.company,
                location = it.location,
                mdFile = mdContent,
                images = it.images.map { image ->
                    val file = findFile(zip, image)
                        ?: fail("No se encontró $image")

                    StorageImage(
                        file = file,
                        path = image,
                        contentHash = calculateHash(file)
                    )
                }
            )
        }.toList()
    }
    fun calculateHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }
    private suspend fun findFile(zip: Path, filename: String): File? = withContext(Dispatchers.IO) {
        ZipInputStream(Files.newInputStream(zip)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (!entry.isDirectory && matches(entry.name, filename)) {
                    val tempFile = Files.createTempFile(
                        "zip-file-",
                        "-${filename.substringAfterLast("/")}"
                    ).toFile()
                    tempFile.outputStream().use { output ->
                        stream.copyTo(output)
                    }
                    return@withContext tempFile
                }
                stream.closeEntry()
            }
        }
        null
    }

    private fun fail(message: String): Nothing {
        log.warning(message)
        throw StorageExperienceException(message)
    }

    /** Acepta "acme/logo.png" y también "owner-repo-sha/acme/logo.png" (zipball de GitHub). */
    private fun matches(entryName: String, filename: String): Boolean =
        entryName == filename ||
            (entryName.contains("/") && entryName.substringAfter("/") == filename)
}