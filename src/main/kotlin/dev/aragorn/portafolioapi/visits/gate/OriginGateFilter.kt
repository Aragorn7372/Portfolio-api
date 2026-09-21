package dev.aragorn.portafolioapi.visits.gate

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.logging.Logger


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class OriginGateFilter(
    @Value("\${app.origin.secret:}")
    private val originSecret: String,
    @Value("\${app.origin.open-paths:/actuator/health}")
    openPaths: String,
) : OncePerRequestFilter() {

    private val matcher = AntPathMatcher()
    private val open = openPaths.split(",").map { it.trim() }.filter { it.isNotBlank() }
    @Volatile
    private var warned = false

    override fun doFilterInternal(
        req: HttpServletRequest,
        res: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (originSecret.isBlank()) {
            if (!warned) {
                warned = true
                log.warning("OriginGate desactivado: app.origin.secret vacío")
            }
            chain.doFilter(req, res)
            return
        }
        val path = req.requestURI
        if (open.any { matcher.match(it, path) }) {
            chain.doFilter(req, res)
            return
        }
        val presented = req.getHeader("X-Origin-Secret")
        val expected = originSecret.toByteArray(StandardCharsets.UTF_8)
        if (presented != null && MessageDigest.isEqual(presented.toByteArray(StandardCharsets.UTF_8), expected)) {
            chain.doFilter(req, res)
            return
        }
        log.warning("Origen no autorizado para $path")
        res.status = HttpServletResponse.SC_FORBIDDEN
        res.contentType = "application/json"
        res.writer.write("""{"error":"forbidden_origin"}""")
    }

    companion object {
        private val log: Logger = Logger.getLogger(OriginGateFilter::class.java.name)
    }
}
