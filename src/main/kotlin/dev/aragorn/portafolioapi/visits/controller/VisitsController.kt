package dev.aragorn.portafolioapi.visits.controller

import dev.aragorn.portafolioapi.visits.gate.ClientIpResolver
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
 * @param clientIpResolver obtiene la IP real del cliente detrás de los proxies.
 */
@RestController
@RequestMapping("/visits")
class VisitsController(
    private val visitsService: VisitsService,
    private val limits: VisitsRateLimitService,
    @Value("\${app.visits.jwt-minutes:15}")
    private val jwtMinutes: Long,
    private val clientIpResolver: ClientIpResolver,
) {

    private val log: Logger = Logger.getLogger(VisitsController::class.java.name)

    /**
     * `POST /visits/track`: registra una visita y emite el token de visita.
     *
     * Es una ruta pública (`auth=false` en `app.gate.rules`). Tiene su propio límite de 10
     * peticiones por minuto por huella e IP.
     *
     * Cuerpo: [TrackSignals] en JSON. Todos los campos son opcionales.
     *
     * Respuestas:
     * - `200 {"counted": true|false, "visits": <total>, "token": <token>}` con
     *   `Set-Cookie: visit_jwt=<token>` (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, caduca en
     *   [jwtMinutes] minutos). El token también va en el cuerpo para los clientes servidos desde otro
     *   sitio (espejos en GitHub Pages o Netlify), donde la cookie `SameSite=Lax` no viaja: esos
     *   clientes lo envían en `Authorization: Bearer <token>`.
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
        val ip = clientIpResolver.resolve(req)
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
            .body(mapOf("counted" to result.counted, "visits" to result.total, "token" to result.token))
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
