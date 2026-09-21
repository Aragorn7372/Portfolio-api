package dev.aragorn.portafolioapi.visits.mapper

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import dev.aragorn.portafolioapi.visits.model.Visits
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class VisitMapperTest {
    private val mapper = VisitMapper()
    private val visit1 = Visits(id = 1, total = 7)
    private val dto1 = VisitResponseDto(7)

    @Test
    fun toDto() {
        val result = mapper.toDto(visit1)
        assertEquals(dto1, result)
    }
}
