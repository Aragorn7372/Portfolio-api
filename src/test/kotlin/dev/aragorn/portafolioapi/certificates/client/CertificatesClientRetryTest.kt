package dev.aragorn.portafolioapi.certificates.client

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class CertificatesClientRetryTest {

    private val builder = RestClient.builder().messageConverters { converters ->
        val mapper = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .build()
        converters.add(0, JacksonJsonHttpMessageConverter(mapper))
    }
    private val mockServer = MockRestServiceServer.bindTo(builder).build()
    private val client = CertificatesClientImpl(builder.build(), "https://example.com/certs")

    @Test
    fun `reintenta ante body vacio y devuelve la lista`() = runTest {
        val json = """
            [{"titulo":"t","url":"https://drive.google.com/file/d/abc123/view","fecha":"2026-03-04"}]
        """.trimIndent()
        mockServer.expect(requestTo("https://example.com/certs")).andRespond(withSuccess())
        mockServer.expect(requestTo("https://example.com/certs"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))

        val result = client.findCertificates()

        assertEquals(1, result.size)
        assertEquals("t", result[0].titulo)
        mockServer.verify()
    }
}
