package dev.aragorn.portafolioapi.visits.mapper

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import dev.aragorn.portafolioapi.visits.model.Visits
import org.springframework.stereotype.Component

/** Conversión de la entidad [Visits] a [VisitResponseDto]. */
@Component
class VisitMapper {
    /**
     * @param visit fila del contador.
     * @return DTO con el total.
     */
    fun toDto(visit: Visits): VisitResponseDto{
        return VisitResponseDto(visit.total)
    }
}