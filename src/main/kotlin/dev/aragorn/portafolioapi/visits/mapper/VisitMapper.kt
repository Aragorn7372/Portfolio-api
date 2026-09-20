package dev.aragorn.portafolioapi.visits.mapper

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import dev.aragorn.portafolioapi.visits.model.Visits
import org.springframework.stereotype.Component

@Component
class VisitMapper {
    fun toDto(visit: Visits): VisitResponseDto{
        return VisitResponseDto(visit.total)
    }
}