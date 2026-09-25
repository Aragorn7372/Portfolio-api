package dev.aragorn.portafolioapi.visits.dto

/**
 * Total de visitas en formato de respuesta.
 *
 * @property total número de visitas únicas contadas.
 */
data class VisitResponseDto(
    val total: Long
)