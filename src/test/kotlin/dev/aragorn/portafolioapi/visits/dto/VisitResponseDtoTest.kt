package dev.aragorn.portafolioapi.visits.dto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class VisitResponseDtoTest {
    private val dto1 = VisitResponseDto(7)

    @Test
    fun equality() {
        val copy = dto1.copy()

        assertEquals(dto1, copy)
        assertEquals(dto1.hashCode(), copy.hashCode())
    }
}
