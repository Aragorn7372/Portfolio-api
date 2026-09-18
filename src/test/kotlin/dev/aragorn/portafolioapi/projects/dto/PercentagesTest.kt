package dev.aragorn.portafolioapi.projects.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PercentagesTest {

    @Test
    fun `convierte bytes a porcentajes`() {
        val result = mapOf(
            "Kotlin" to 72000L,
            "Java" to 18000L,
            "HTML" to 10000L,
        ).toPercentages()

        assertEquals(
            mapOf("Kotlin" to 72.0, "Java" to 18.0, "HTML" to 10.0),
            result,
        )
    }

    @Test
    fun `mapa vacio devuelve mapa vacio`() {
        assertEquals(
            emptyMap<String, Double>(),
            emptyMap<String, Long>().toPercentages(),
        )
    }
}
