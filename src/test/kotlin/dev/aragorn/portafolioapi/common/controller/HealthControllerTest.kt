package dev.aragorn.portafolioapi.common.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class HealthControllerTest {

    private val controller = HealthController()

    @Test
    @DisplayName("health devuelve 200 con status UP")
    fun health() {
        val result = controller.health()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(mapOf("status" to "UP"), result.body)
    }
}
