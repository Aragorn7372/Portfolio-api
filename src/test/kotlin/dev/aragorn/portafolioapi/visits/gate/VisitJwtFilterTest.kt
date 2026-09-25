package dev.aragorn.portafolioapi.visits.gate

import dev.aragorn.portafolioapi.visits.ratelimit.VisitsRateLimitService
import dev.aragorn.portafolioapi.visits.service.VisitsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.context.SecurityContextHolder
import java.io.PrintWriter
import java.io.StringWriter

@ExtendWith(MockitoExtension::class)
class VisitJwtFilterTest {
    @Mock
    private lateinit var visitsService: VisitsService
    @Mock
    private lateinit var limits: VisitsRateLimitService
    @Mock
    private lateinit var req: HttpServletRequest
    @Mock
    private lateinit var res: HttpServletResponse
    @Mock
    private lateinit var chain: FilterChain

    private val ip = "1.2.3.4"
    private val fp = "fp1"
    private val token = "tok1"
    private val closedRule = GateRule(pattern = "/zona/**", auth = true)
    private val fpBucket = "rl:fp:$fp:zona-**"
    private val ipBucket = "rl:ip:$ip:global"

    private val body = StringWriter()

    private fun givenFilter(vararg rules: GateRule) =
        VisitJwtFilter(visitsService, limits, VisitGateProperties(rules.toList()), ClientIpResolver(""))

    private fun givenRequest(path: String) {
        whenever(req.method).thenReturn("GET")
        whenever(req.requestURI).thenReturn(path)
    }

    private fun givenWriter() {
        whenever(res.writer).thenReturn(PrintWriter(body))
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("sin regla deja pasar")
    fun filterNoRule() {
        val filter = givenFilter()
        givenRequest("/publico")

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
        verify(visitsService, times(0)).validateToken(any())
        verify(limits, times(0)).allow(any(), any())
    }

    @Test
    @DisplayName("regla sin auth deja pasar")
    fun filterOpenRule() {
        val filter = givenFilter(GateRule(pattern = "/abierto/**", auth = false))
        givenRequest("/abierto/x")

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
        verify(visitsService, times(0)).validateToken(any())
        verify(limits, times(0)).allow(any(), any())
    }

    @Test
    @DisplayName("sin token responde 401")
    fun filterNoToken() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(res).contentType = "application/json"
        assertEquals(
            """{"error":"visit_token_required","hint":"POST /visits/track"}""",
            body.toString()
        )
        verify(chain, times(0)).doFilter(req, res)
        verify(visitsService, times(0)).validateToken(any())
    }

    @Test
    @DisplayName("token inválido responde 401")
    fun filterBadToken() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        whenever(req.cookies).thenReturn(arrayOf(Cookie("visit_jwt", "malo")))
        whenever(visitsService.validateToken("malo")).thenReturn(null)
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = HttpServletResponse.SC_UNAUTHORIZED
        assertEquals(
            """{"error":"visit_token_required","hint":"POST /visits/track"}""",
            body.toString()
        )
        verify(chain, times(0)).doFilter(req, res)
    }

    @Test
    @DisplayName("fp sin cupo responde 429")
    fun filterFpLimited() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        whenever(req.cookies).thenReturn(arrayOf(Cookie("visit_jwt", token)))
        whenever(visitsService.validateToken(token)).thenReturn(fp)
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(limits.allow(fpBucket, 120)).thenReturn(false)
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = 429
        verify(res).setHeader("Retry-After", "60")
        verify(res).contentType = "application/json"
        assertEquals("""{"error":"rate_limited"}""", body.toString())
        verify(limits, times(0)).allow(ipBucket, 30)
        verify(chain, times(0)).doFilter(req, res)
    }

    @Test
    @DisplayName("ip sin cupo responde 429")
    fun filterIpLimited() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        whenever(req.getHeader("Authorization")).thenReturn("Bearer $token")
        whenever(visitsService.validateToken(token)).thenReturn(fp)
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(limits.allow(fpBucket, 120)).thenReturn(true)
        whenever(limits.allow(ipBucket, 30)).thenReturn(false)
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = 429
        verify(res).setHeader("Retry-After", "60")
        assertEquals("""{"error":"rate_limited"}""", body.toString())
        verify(chain, times(0)).doFilter(req, res)
    }

    @Test
    @DisplayName("el cupo por ip usa la ip real del proxy, no remoteAddr")
    fun filterIpFromProxyHeader() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        whenever(req.cookies).thenReturn(arrayOf(Cookie("visit_jwt", token)))
        whenever(visitsService.validateToken(token)).thenReturn(fp)
        whenever(req.getHeader("Authorization")).thenReturn(null)
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn("9.9.9.9")
        whenever(limits.allow(fpBucket, 120)).thenReturn(true)
        whenever(limits.allow("rl:ip:9.9.9.9:global", 30)).thenReturn(true)

        filter.doFilter(req, res, chain)

        verify(limits, times(1)).allow("rl:ip:9.9.9.9:global", 30)
        verify(req, times(0)).remoteAddr
        verify(chain, times(1)).doFilter(req, res)
    }

    @Test
    @DisplayName("token válido deja pasar con auth")
    fun filterOk() {
        val filter = givenFilter(closedRule)
        givenRequest("/zona/x")
        whenever(req.cookies).thenReturn(arrayOf(Cookie("visit_jwt", token)))
        whenever(visitsService.validateToken(token)).thenReturn(fp)
        whenever(req.remoteAddr).thenReturn(ip)
        whenever(limits.allow(fpBucket, 120)).thenReturn(true)
        whenever(limits.allow(ipBucket, 30)).thenReturn(true)

        filter.doFilter(req, res, chain)

        verify(req, times(1)).setAttribute("visitFp", fp)
        verify(chain, times(1)).doFilter(req, res)
        assertEquals(fp, SecurityContextHolder.getContext().authentication?.principal)
    }
}
