package dev.aragorn.portafolioapi.visits.controller

import dev.aragorn.portafolioapi.visits.ratelimit.VisitsRateLimitService
import dev.aragorn.portafolioapi.visits.service.TrackSignals
import dev.aragorn.portafolioapi.visits.service.VisitsService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.logging.Logger

/**
 * Endpoints del contador de visitas. `POST /visits/track` es además la puerta de entrada que
 * emite el token de visita.
 *
 * @param visitsService servicio de visitas.
 * @param limits servicio de límite de peticiones.
 * @param jwtMinutes duración de la cookie del token (`app.visits.jwt-minutes`, 15 por defecto).
 * @param trustedProxies IPs de los proxies de confianza, separadas por comas
 *   (`app.visits.trusted-proxies`). Solo si la petición llega desde una de ellas se hace caso a
 *   `X-Forwarded-For`.
 */
@RestController
@RequestMapping("/visits")
class VisitsController(
    private val visitsService: VisitsService,
    private val limits: VisitsRateLimitService,
    @Value("\${app.visits.jwt-minutes:15}")
    private val jwtMinutes: Long,
    @Value("\${app.visits.trusted-proxies:}")
    private val trustedProxies: String,
) {

    private val log: Logger = Logger.getLogger(VisitsController::class.java.name)

    /**
     * Obtiene la IP real del cliente.
     *
     * Orden de preferencia:
     * 1. La cabecera con la IP del cliente que añade el proxy perimetral de confianza. Solo puede
     *    llegar a través de ese proxy, porque [dev.aragorn.portafolioapi.visits.gate.OriginGateFilter]
     *    bloquea las peticiones directas.
     * 2. La primera IP de `X-Forwarded-For`, pero solo si la conexión viene de uno de
     *    [trustedProxies]. Si no, alguien podría falsificar su IP con esa cabecera.
     * 3. La IP de la conexión TCP (`remoteAddr`).
     *
     * @param req petición entrante.
     * @return la IP del cliente, o `"unknown"` si no se puede saber.
     */
    private fun clientIp(req: HttpServletRequest): String {
        req.getHeader("CF-Connecting-IP")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val forwarded = req.getHeader("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim().orEmpty()
        val remote = req.remoteAddr ?: "unknown"
        val trusted = trustedProxies.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return if (forwarded.isNotBlank() && remote in trusted) forwarded else remote
    }

    /**
     * `POST /visits/track`: registra una visita y emite el token de visita.
     *
     * Es una ruta pública (`auth=false` en `app.gate.rules`). Tiene su propio límite de 10
     * peticiones por minuto por huella e IP.
     *
     * Cuerpo: [TrackSignals] en JSON. Todos los campos son opcionales.
     *
     * Respuestas:
     * - `200 {"counted": true|false, "visits": <total>}` con `Set-Cookie: visit_jwt=<token>`
     *   (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, caduca en [jwtMinutes] minutos).
     * - `400 {"error":"validation_failed", ...}`: alguna señal supera el tamaño máximo.
     * - `429 {"error":"rate_limited"}` con `Retry-After: 60`.
     *
     * @param signals señales del navegador.
     * @param jwt cookie `visit_jwt` actual, si hay. Sirve para detectar recargas.
     * @param req petición, de la que se saca la IP.
     * @return el resultado de la visita.
     */
    @PostMapping("/track")
    suspend fun track(
        @Valid @RequestBody signals: TrackSignals,
        @CookieValue(name = "visit_jwt", required = false) jwt: String?,
        req: HttpServletRequest,
    ): ResponseEntity<Map<String, Any>> {
        val ip = clientIp(req)
        val fp = visitsService.fingerprint(signals, ip)
        if (!limits.allow("rl:track:$fp:$ip", 10)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(mapOf("error" to "rate_limited"))
        }
        log.info("Tracking visit")
        val result = visitsService.track(signals, ip, jwt)
        val cookie = ResponseCookie.from("visit_jwt", result.token)
            .httpOnly(true).secure(true).sameSite("Lax").path("/")
            .maxAge(Duration.ofMinutes(jwtMinutes)).build()
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(mapOf("counted" to result.counted, "visits" to result.total))
    }

    /**
     * `GET /visits/count`: devuelve el total de visitas sin registrar ninguna.
     *
     * Con la configuración por defecto exige token de visita (`auth=true` en `app.gate.rules`).
     *
     * Respuestas:
     * - `200 {"visits": <total>}`.
     * - `401 {"error":"visit_token_required"}` / `429 {"error":"rate_limited"}` según [dev.aragorn.portafolioapi.visits.gate.VisitJwtFilter].
     *
     * @return el total de visitas.
     */
    @GetMapping("/count")
    suspend fun count(): ResponseEntity<Map<String, Long>> {
        log.info("Getting visits count")
        return ResponseEntity.ok(mapOf("visits" to visitsService.total()))
    }
}
