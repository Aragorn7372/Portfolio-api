package dev.aragorn.portafolioapi.visits.gate

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.context.SecurityContextHolder
import java.io.PrintWriter
import java.io.StringWriter

@ExtendWith(MockitoExtension::class)
class OriginGateFilterTest {
    @Mock
    private lateinit var req: HttpServletRequest
    @Mock
    private lateinit var res: HttpServletResponse
    @Mock
    private lateinit var chain: FilterChain

    private val secret = "s3cr3t"
    private val body = StringWriter()

    private fun givenFilter(secret: String = this.secret) =
        OriginGateFilter(secret, "/actuator/health")

    private fun givenWriter() {
        whenever(res.writer).thenReturn(PrintWriter(body))
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("con secreto deja pasar")
    fun filterPass() {
        val filter = givenFilter()
        whenever(req.requestURI).thenReturn("/projects")
        whenever(req.getHeader("X-Origin-Secret")).thenReturn(secret)

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
    }

    @Test
    @DisplayName("sin secreto responde 403")
    fun filterMissing() {
        val filter = givenFilter()
        whenever(req.requestURI).thenReturn("/projects")
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = HttpServletResponse.SC_FORBIDDEN
        verify(res).contentType = "application/json"
        assertEquals("""{"error":"forbidden_origin"}""", body.toString())
        verify(chain, times(0)).doFilter(req, res)
    }

    @Test
    @DisplayName("secreto erróneo responde 403")
    fun filterWrong() {
        val filter = givenFilter()
        whenever(req.requestURI).thenReturn("/projects")
        whenever(req.getHeader("X-Origin-Secret")).thenReturn("otro")
        givenWriter()

        filter.doFilter(req, res, chain)

        verify(res).status = HttpServletResponse.SC_FORBIDDEN
        assertEquals("""{"error":"forbidden_origin"}""", body.toString())
        verify(chain, times(0)).doFilter(req, res)
    }

    @Test
    @DisplayName("ruta abierta deja pasar sin secreto")
    fun filterHealth() {
        val filter = givenFilter()
        whenever(req.requestURI).thenReturn("/actuator/health")

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
    }

    @Test
    @DisplayName("sin secreto configurado queda desactivado")
    fun filterDisabled() {
        val filter = givenFilter(secret = "")

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
    }
}
