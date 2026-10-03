package dev.aragorn.portafolioapi.experience.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class ExperienceJsonDeserializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `deserializa el experience json del repositorio`() {
        val json = """
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

        val result = mapper.readValue<List<ExperienceJsonDto>>(json)

        assertEquals(1, result.size)
        assertEquals(
            ExperienceJsonDto(
                id = "acme",
                title = "Backend Developer",
                company = "Acme",
                location = "Madrid",
                mdFile = "acme/acme.md",
                images = listOf("acme/logo.png", "acme/foto.jpg")
            ),
            result[0]
        )
    }
}
