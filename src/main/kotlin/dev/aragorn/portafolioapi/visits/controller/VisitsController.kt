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

    private fun clientIp(req: HttpServletRequest): String {
        req.getHeader("CF-Connecting-IP")?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val forwarded = req.getHeader("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim().orEmpty()
        val remote = req.remoteAddr ?: "unknown"
        val trusted = trustedProxies.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return if (forwarded.isNotBlank() && remote in trusted) forwarded else remote
    }

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

    @GetMapping("/count")
    suspend fun count(): ResponseEntity<Map<String, Long>> {
        log.info("Getting visits count")
        return ResponseEntity.ok(mapOf("visits" to visitsService.total()))
    }
}
