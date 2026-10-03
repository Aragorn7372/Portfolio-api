package dev.aragorn.portafolioapi.experience.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.InputStream
import java.lang.RuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger

@Component
class ExperienceClientImpl(
    @Value($$"${app.experience.url}") private val experienceUrl: String,
    @Qualifier("githubExperienceClient") private val restClient: RestClient,
) : ExperienceClient {
    private val log: Logger = Logger.getLogger(ExperienceClientImpl::class.java.name)

    override suspend fun getExperience(): Path = withContext(Dispatchers.IO) {
        if (experienceUrl.isBlank()) {
            log.severe("APP_EXPERIENCE_URL no está configurado")
            throw IllegalArgumentException("APP_EXPERIENCE_URL no está configurado")
        }

        log.info("downloading experience zip from $experienceUrl")
        val tempFile = Files.createTempFile("experience-", ".zip")

        try {
            restClient.get()
                .uri(experienceUrl)
                .retrieve()
                .body(InputStream::class.java)
                ?.use { input ->
                    Files.copy(
                        input,
                        tempFile,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                ?: throw RuntimeException("No se pudo cargar el zip")

            log.info("experience zip downloaded (${Files.size(tempFile)} bytes)")
            tempFile
        } catch (e: Exception) {
            log.warning("Error downloading experience zip: ${e.message}")
            Files.deleteIfExists(tempFile)
            throw e
        }
    }
}
