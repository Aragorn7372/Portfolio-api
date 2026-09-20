package dev.aragorn.portafolioapi.visits.service

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import jakarta.validation.constraints.Size

data class TrackSignals(
    @field:Size(max = 512)
    val userAgent: String = "",
    @field:Size(max = 32)
    val language: String = "",
    @field:Size(max = 64)
    val timezone: String = "",
    @field:Size(max = 32)
    val screen: String = "",
    @field:Size(max = 50)
    val plugins: List<@Size(max = 128) String> = emptyList(),
)

data class TrackResult(
    val counted: Boolean,
    val total: Long,
    val token: String,
)

interface VisitsService {
    suspend fun get(): VisitResponseDto
    suspend fun total(): Long

    /** JWT/fingerprint son solo CPU: no-suspend para poder usarse desde el filtro. */
    fun fingerprint(signals: TrackSignals, ip: String): String
    fun issueToken(fingerprint: String): String

    /** Devuelve el fingerprint si firma y expiración son válidas, null si no. */
    fun validateToken(token: String): String?
    suspend fun track(signals: TrackSignals, ip: String, incomingJwt: String?): TrackResult
}
