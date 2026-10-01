package dev.aragorn.portafolioapi.visits.service

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import jakarta.validation.constraints.Size

/**
 * Señales del navegador que envía el cliente en `POST /visits/track` para calcular su huella.
 *
 * Todas son opcionales y tienen un tamaño máximo, para que nadie pueda mandar cuerpos enormes.
 * Ninguna identifica a una persona por sí sola: combinadas con la IP, sirven para distinguir
 * visitantes y no contar dos veces la misma visita.
 *
 * @property userAgent `User-Agent` del navegador (máximo 512 caracteres).
 * @property language idioma preferido, por ejemplo `es-ES` (máximo 32).
 * @property timezone zona horaria IANA, por ejemplo `Europe/Madrid` (máximo 64).
 * @property screen resolución de pantalla, por ejemplo `1920x1080` (máximo 32). Se acepta por
 *   compatibilidad con clientes antiguos, pero no forma parte de la huella (no es estable).
 * @property plugins nombres de plugins del navegador (máximo 50, de 128 caracteres cada uno).
 */
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

/**
 * Resultado de registrar una visita.
 *
 * @property counted `true` si la visita se ha contado ahora. `false` si era una visita repetida
 *   (recargar la página, abrir otra pestaña...).
 * @property total total de visitas después de la operación.
 * @property token token de visita (JWT) que el cliente debe usar en las siguientes peticiones.
 */
data class TrackResult(
    val counted: Boolean,
    val total: Long,
    val token: String,
)

/**
 * Contador de visitas únicas y emisión de los tokens de visita.
 *
 * Una visita es única por huella ([fingerprint]) durante `app.visits.jwt-minutes` minutos: en ese
 * tiempo, las recargas y peticiones repetidas del mismo visitante no suman.
 *
 * @see VisitsServiceImpl
 */
interface VisitsService {
    /**
     * Devuelve el contador como DTO.
     *
     * @return el total de visitas, o `0` si todavía no existe el contador.
     */
    suspend fun get(): VisitResponseDto

    /**
     * Devuelve el total de visitas.
     *
     * @return el total, o `0` si todavía no existe el contador.
     */
    suspend fun total(): Long

    /**
     * Calcula la huella de un visitante: SHA-256 en hexadecimal de sus señales normalizadas más la IP.
     *
     * Es determinista: las mismas señales con la misma IP dan siempre la misma huella. No se
     * guardan ni la IP ni las señales en claro, solo este hash.
     *
     * @param signals señales del navegador.
     * @param ip IP del cliente.
     * @return la huella (64 caracteres hexadecimales).
     */
    fun fingerprint(signals: TrackSignals, ip: String): String

    /**
     * Emite un token de visita firmado para una huella.
     *
     * @param fingerprint huella del visitante.
     * @return JWT firmado con HMAC, con claim `fp` y caducidad de `app.visits.jwt-minutes` minutos.
     */
    fun issueToken(fingerprint: String): String

    /**
     * Comprueba un token de visita y extrae su huella.
     *
     * @param token JWT recibido.
     * @return la huella del claim `fp`, o `null` si el token no es válido, ha caducado o está mal firmado.
     */
    fun validateToken(token: String): String?

    /**
     * Registra una visita y devuelve un token para las siguientes peticiones.
     *
     * @param signals señales del navegador.
     * @param ip IP del cliente.
     * @param incomingJwt token que ya traía el cliente, si tenía.
     * @return si se ha contado, el total actual y el token que se debe guardar.
     */
    suspend fun track(signals: TrackSignals, ip: String, incomingJwt: String?): TrackResult
}
