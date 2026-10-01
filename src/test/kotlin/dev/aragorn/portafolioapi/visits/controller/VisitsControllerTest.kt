package dev.aragorn.portafolioapi.visits.controller

import dev.aragorn.portafolioapi.visits.gate.ClientIpResolver
import dev.aragorn.portafolioapi.visits.ratelimit.VisitsRateLimitService
import dev.aragorn.portafolioapi.visits.service.TrackResult
import dev.aragorn.portafolioapi.visits.service.TrackSignals
import dev.aragorn.portafolioapi.visits.service.VisitsService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class VisitsControllerTest {
    @Mock
    private lateinit var visitsService: VisitsService
    @Mock
    private lateinit var limits: VisitsRateLimitService
    @Mock
    private lateinit var req: HttpServletRequest

    private val jwtMinutes = 15L

    private lateinit var controller: VisitsController

    private val signals = TrackSignals(
        userAgent = "Mozilla/5.0",
        language = "es-ES",
        timezone = "Europe/Madrid",
        screen = "1920x1080",
    )
    private val ip = "1.2.3.4"
    private val fp = "fp1"

    private fun givenController(trustedProxies: String = "") {
        controller = VisitsController(visitsService, limits, jwtMinutes, ClientIpResolver(trustedProxies))
    }

    @Test
    @DisplayName("track bien, cuenta y devuelve el token en cookie y en el cuerpo")
    fun track() = runTest {
        givenController()
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(visitsService.fingerprint(signals, ip)).thenReturn(fp)
        whenever(limits.allow("rl:track:$fp:$ip", 10)).thenReturn(true)
        whenever(visitsService.track(signals, ip, "jwt-in")).thenReturn(TrackResult(true, 5L, "tok"))

        val result = controller.track(signals, "jwt-in", req)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(mapOf("counted" to true, "visits" to 5L, "token" to "tok"), result.body)
        assertTrue(result.headers.getFirst(HttpHeaders.SET_COOKIE)!!.contains("visit_jwt=tok"))
        verify(visitsService, times(1)).track(signals, ip, "jwt-in")
    }

    @Test
    @DisplayName("track con rate limit responde 429")
    fun trackLimited() = runTest {
        givenController()
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(visitsService.fingerprint(signals, ip)).thenReturn(fp)
        whenever(limits.allow("rl:track:$fp:$ip", 10)).thenReturn(false)

        val result = controller.track(signals, null, req)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, result.statusCode)
        assertEquals("60", result.headers.getFirst("Retry-After"))
        assertEquals(mapOf("error" to "rate_limited"), result.body)
        verify(visitsService, times(0)).track(signals, ip, null)
    }

    @Test
    @DisplayName("track con proxy de confianza usa la ip reenviada")
    fun trackTrustedProxy() = runTest {
        givenController("1.2.3.4")
        whenever(req.remoteAddr).thenReturn("1.2.3.4")
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn("")
        whenever(req.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 1.1.1.1")
        whenever(visitsService.fingerprint(signals, "9.9.9.9")).thenReturn(fp)
        whenever(limits.allow("rl:track:$fp:9.9.9.9", 10)).thenReturn(true)
        whenever(visitsService.track(signals, "9.9.9.9", null)).thenReturn(TrackResult(true, 6L, "tok"))

        val result = controller.track(signals, null, req)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(mapOf("counted" to true, "visits" to 6L, "token" to "tok"), result.body)
        verify(visitsService, times(1)).fingerprint(signals, "9.9.9.9")
    }

    @Test
    @DisplayName("track con CF-Connecting-IP usa esa ip")
    fun trackCloudflare() = runTest {
        givenController()
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn("9.9.9.9")
        whenever(visitsService.fingerprint(signals, "9.9.9.9")).thenReturn(fp)
        whenever(limits.allow("rl:track:$fp:9.9.9.9", 10)).thenReturn(true)
        whenever(visitsService.track(signals, "9.9.9.9", null)).thenReturn(TrackResult(true, 6L, "tok"))

        val result = controller.track(signals, null, req)

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(mapOf("counted" to true, "visits" to 6L, "token" to "tok"), result.body)
        verify(visitsService, times(1)).fingerprint(signals, "9.9.9.9")
        verify(req, times(0)).getHeader("X-Forwarded-For")
    }

    @Test
    @DisplayName("track con CF en blanco usa el remoto")
    fun trackCloudflareBlank() = runTest {
        givenController()
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn("")
        whenever(visitsService.fingerprint(signals, ip)).thenReturn(fp)
        whenever(limits.allow("rl:track:$fp:$ip", 10)).thenReturn(true)
        whenever(visitsService.track(signals, ip, null)).thenReturn(TrackResult(true, 5L, "tok"))

        val result = controller.track(signals, null, req)

        assertEquals(HttpStatus.OK, result.statusCode)
        verify(visitsService, times(1)).fingerprint(signals, ip)
    }

    @Test
    @DisplayName("count devuelve el total")
    fun count() = runTest {
        givenController()
        whenever(visitsService.total()).thenReturn(42L)

        val result = controller.count()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(mapOf("visits" to 42L), result.body)
    }
}
