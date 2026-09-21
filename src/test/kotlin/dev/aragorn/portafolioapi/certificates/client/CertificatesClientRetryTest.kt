package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.exceptions.CertificationEmptyException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.SocketTimeoutException

class CertificatesClientRetryTest {

    private val baseUrl = "https://example.com/certs"
    private val builder = RestClient.builder().messageConverters { converters ->
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .build()
        converters.add(0, JacksonJsonHttpMessageConverter(mapper))
    }
    private val mockServer = MockRestServiceServer.bindTo(builder).build()
    private val client = CertificatesClientImpl(builder.build(), baseUrl)

    private val certificateDto1 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        "2025-03-15"
    )
    private val certificateDto2 = CertificatesResponseDto(
        "Spring Boot Developer",
        "https://drive.google.com/file/d/spring-boot-456/view",
        "2025-05-20"
    )
    private val jsonLista = """
        [
            {"titulo":"Kotlin Developer","url":"https://drive.google.com/file/d/kotlin-developer-123/view","fecha":"2025-03-15"},
            {"titulo":"Spring Boot Developer","url":"https://drive.google.com/file/d/spring-boot-456/view","fecha":"2025-05-20"}
        ]
    """.trimIndent()

    @Test
    fun findCertificates() = runTest {
        mockServer.expect(requestTo(baseUrl))
            .andRespond(withSuccess(jsonLista, MediaType.APPLICATION_JSON))

        val result = client.findCertificates()

        assertEquals(listOf(certificateDto1, certificateDto2), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("reintenta ante body vacío y devuelve la lista")
    fun findCertificatesRetry() = runTest {
        mockServer.expect(requestTo(baseUrl)).andRespond(withSuccess())
        mockServer.expect(requestTo(baseUrl))
            .andRespond(withSuccess(jsonLista, MediaType.APPLICATION_JSON))

        val result = client.findCertificates()

        assertEquals(listOf(certificateDto1, certificateDto2), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("body vacío tres veces lanza CertificationEmptyException")
    fun findCertificatesEmptyBody() = runTest {
        mockServer.expect(requestTo(baseUrl)).andRespond(withSuccess())
        mockServer.expect(requestTo(baseUrl)).andRespond(withSuccess())
        mockServer.expect(requestTo(baseUrl)).andRespond(withSuccess())

        val exception = assertThrows<CertificationEmptyException> {
            client.findCertificates()
        }

        assertTrue(exception.message!!.contains("respuesta vacia"))
        mockServer.verify()
    }

    @Test
    @DisplayName("lista vacía lanza CertificationEmptyException")
    fun findCertificatesEmptyList() = runTest {
        mockServer.expect(requestTo(baseUrl))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val exception = assertThrows<CertificationEmptyException> {
            client.findCertificates()
        }

        assertEquals("No puede llegar la lista vacia", exception.message)
        mockServer.verify()
    }

    @Test
    @DisplayName("404 lanza CertificationEmptyException")
    fun findCertificatesNotFound() = runTest {
        mockServer.expect(requestTo(baseUrl))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        val exception = assertThrows<CertificationEmptyException> {
            client.findCertificates()
        }

        assertEquals("endpoint de certificados no encontrado (404): $baseUrl", exception.message)
        mockServer.verify()
    }

    @Test
    @DisplayName("500 y luego éxito reintenta y devuelve la lista")
    fun findCertificatesServerErrorRetry() = runTest {
        mockServer.expect(requestTo(baseUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(baseUrl))
            .andRespond(withSuccess(jsonLista, MediaType.APPLICATION_JSON))

        val result = client.findCertificates()

        assertEquals(listOf(certificateDto1, certificateDto2), result)
        mockServer.verify()
    }

    @Test
    @DisplayName("500 tres veces relanza la excepción")
    fun findCertificatesServerError() = runTest {
        mockServer.expect(requestTo(baseUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(baseUrl)).andRespond(withServerError())
        mockServer.expect(requestTo(baseUrl)).andRespond(withServerError())

        assertThrows<HttpStatusCodeException> {
            client.findCertificates()
        }

        mockServer.verify()
    }

    @Test
    @DisplayName("timeout tres veces relanza la excepción")
    fun findCertificatesTimeout() = runTest {
        mockServer.expect(requestTo(baseUrl)).andRespond(withException(SocketTimeoutException("timeout")))
        mockServer.expect(requestTo(baseUrl)).andRespond(withException(SocketTimeoutException("timeout")))
        mockServer.expect(requestTo(baseUrl)).andRespond(withException(SocketTimeoutException("timeout")))

        assertThrows<ResourceAccessException> {
            client.findCertificates()
        }

        mockServer.verify()
    }

    @Test
    @DisplayName("base url en blanco lanza IllegalArgumentException")
    fun findCertificatesBlankBaseUrl() = runTest {
        val blankClient = CertificatesClientImpl(builder.build(), "")

        val exception = assertThrows<IllegalArgumentException> {
            blankClient.findCertificates()
        }

        assertEquals("APP_CERTIFICATES_BASE_URL no está configurado", exception.message)
    }
}
