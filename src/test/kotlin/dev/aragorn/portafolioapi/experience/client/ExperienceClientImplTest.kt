package dev.aragorn.portafolioapi.experience.client

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.nio.file.Files

class ExperienceClientImplTest {
    private val baseUrl = "https://api.github.com"
    private val experienceUrl = "/repos/test-user/test-repo/zipball/main"
    private val builder = RestClient.builder().baseUrl(baseUrl)
    private val mockServer = MockRestServiceServer.bindTo(builder).build()
    private val client = ExperienceClientImpl(experienceUrl, builder.build())

    private val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x0A, 0x00)

    @Test
    @DisplayName("getExperience bien, descarga el zip en un fichero temporal")
    fun getExperience() = runTest {
        mockServer.expect(requestTo(baseUrl + experienceUrl))
            .andRespond(withSuccess(zipBytes, MediaType.APPLICATION_OCTET_STREAM))

        val result = client.getExperience()

        try {
            assertTrue(Files.exists(result))
            assertTrue(result.fileName.toString().startsWith("experience-"))
            assertTrue(result.fileName.toString().endsWith(".zip"))
            assertArrayEquals(zipBytes, Files.readAllBytes(result))
        } finally {
            Files.deleteIfExists(result)
        }
        mockServer.verify()
    }

    @Test
    @DisplayName("getExperience mal, url en blanco lanza IllegalArgumentException")
    fun getExperienceBlankUrl() = runTest {
        val blankClient = ExperienceClientImpl("", builder.build())

        val exception = assertThrows<IllegalArgumentException> {
            blankClient.getExperience()
        }

        assertEquals("APP_EXPERIENCE_URL no está configurado", exception.message)
    }

    @Test
    @DisplayName("getExperience mal, 404 relanza HttpClientErrorException")
    fun getExperienceNotFound() = runTest {
        mockServer.expect(requestTo(baseUrl + experienceUrl))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThrows<HttpClientErrorException> {
            client.getExperience()
        }

        mockServer.verify()
    }

    @Test
    @DisplayName("getExperience mal, 500 relanza HttpServerErrorException")
    fun getExperienceServerError() = runTest {
        mockServer.expect(requestTo(baseUrl + experienceUrl))
            .andRespond(withServerError())

        assertThrows<HttpServerErrorException> {
            client.getExperience()
        }

        mockServer.verify()
    }
}
