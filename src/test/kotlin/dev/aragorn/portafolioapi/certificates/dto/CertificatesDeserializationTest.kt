package dev.aragorn.portafolioapi.certificates.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class CertificatesDeserializationTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    @Test
    fun `deserializa el payload de ejemplo de la API`() {
        val json = """
            [
              {
                "titulo": "certificado_curso_de_solid_y_patrones_de_diseño",
                "url": "https://drive.google.com/file/d/1lN2gldF5lFFmivFChatvLhrukiOq0_ix/view?usp=drivesdk",
                "fecha": "2026-03-04"
              }
            ]
        """.trimIndent()

        val result = mapper.readValue(json, object : TypeReference<List<CertificatesResponseDto>>() {})

        assertEquals(1, result.size)
        assertEquals("certificado_curso_de_solid_y_patrones_de_diseño", result[0].titulo)
        assertEquals(
            "https://drive.google.com/file/d/1lN2gldF5lFFmivFChatvLhrukiOq0_ix/view?usp=drivesdk",
            result[0].url,
        )
        assertEquals("2026-03-04", result[0].fecha)
    }
}
