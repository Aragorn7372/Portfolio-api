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


/**
 * Filtro que solo deja pasar las peticiones que llegan a través del proxy perimetral de confianza.
 *
 * El proxy añade a cada petición la cabecera `X-Origin-Secret` con un secreto compartido. Este
 * filtro la compara en tiempo constante ([MessageDigest.isEqual]) con `app.origin.secret` y, si
 * no coincide, corta con `403 {"error":"forbidden_origin"}`. Así se evita que alguien llame a la
 * API directamente saltándose el proxy y sus protecciones.
 *
 * - Si `app.origin.secret` está vacío, el filtro queda desactivado (entornos locales y CI) y lo
 *   avisa una sola vez en el log.
 * - Las rutas de `app.origin.open-paths` (por defecto `/actuator/health`) siempre pasan, para que
 *   funcionen los health checks de la plataforma de despliegue, que no pasan por el proxy.
 *
 * Se ejecuta con [Ordered.HIGHEST_PRECEDENCE], antes que cualquier otro filtro, incluido Spring Security.
 *
 * @param originSecret secreto esperado. Si está vacío, el filtro queda desactivado.
 * @param openPaths patrones Ant, separados por comas, que no necesitan el secreto.
 */
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

    /** Para avisar solo una vez en el log de que el filtro está desactivado. */
    @Volatile
    private var warned = false

    /**
     * Comprueba el secreto de origen y deja pasar la petición o la rechaza con `403`.
     *
     * @param req petición entrante.
     * @param res respuesta. Se escribe directamente si se rechaza la petición.
     * @param chain resto de la cadena de filtros.
     */
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
