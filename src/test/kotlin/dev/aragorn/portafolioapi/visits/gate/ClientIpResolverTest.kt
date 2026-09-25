package dev.aragorn.portafolioapi.visits.gate

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ClientIpResolverTest {
    @Mock
    private lateinit var req: HttpServletRequest

    @Test
    @DisplayName("usa la cabecera del proxy perimetral si viene")
    fun proxyHeader() {
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn(" 9.9.9.9 ")

        assertEquals("9.9.9.9", ClientIpResolver("").resolve(req))
    }

    @Test
    @DisplayName("sin cabecera ni proxy de confianza usa remoteAddr")
    fun remoteAddr() {
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn(null)
        whenever(req.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9")
        whenever(req.remoteAddr).thenReturn("1.2.3.4")

        assertEquals("1.2.3.4", ClientIpResolver("").resolve(req))
    }

    @Test
    @DisplayName("X-Forwarded-For solo cuenta desde un proxy de confianza")
    fun trustedForwarded() {
        whenever(req.getHeader("CF-Connecting-IP")).thenReturn("")
        whenever(req.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 1.1.1.1")
        whenever(req.remoteAddr).thenReturn("10.0.0.1")

        assertEquals("9.9.9.9", ClientIpResolver("10.0.0.1, 10.0.0.2").resolve(req))
    }

    @Test
    @DisplayName("sin remoteAddr devuelve unknown")
    fun unknown() {
        assertEquals("unknown", ClientIpResolver("").resolve(req))
    }
}
