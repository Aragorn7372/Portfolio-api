package dev.aragorn.portafolioapi.visits.gate

import dev.aragorn.portafolioapi.visits.ratelimit.VisitsRateLimitService
import dev.aragorn.portafolioapi.visits.service.VisitsService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.util.logging.Logger

/**
 * Filtro que protege los endpoints de datos con el token de visita y aplica los límites de peticiones.
 *
 * El token lo emite `POST /visits/track` (ver
 * [dev.aragorn.portafolioapi.visits.controller.VisitsController.track]). Así solo pueden consumir
 * la API los clientes que antes han pasado por la web y se han registrado como visita, lo que
 * dificulta el scraping directo.
 *
 * Para cada petición:
 * 1. Busca la primera [GateRule] cuyo patrón coincide con la ruta. Si no hay ninguna o tiene
 *    `auth=false`, deja pasar la petición.
 * 2. Lee el token de la cookie `visit_jwt` o, si no está, de la cabecera
 *    `Authorization: Bearer <token>`.
 * 3. Si el token falta, está caducado o su firma no es válida, responde
 *    `401 {"error":"visit_token_required","hint":"POST /visits/track"}`.
 * 4. Aplica dos límites por minuto con [VisitsRateLimitService]: uno por huella y grupo de rutas
 *    ([GateRule.fpPerMinute]) y otro global por IP real del cliente ([GateRule.ipPerMinute],
 *    obtenida con [ClientIpResolver]). Si se supera
 *    alguno, responde `429 {"error":"rate_limited"}` con `Retry-After: 60`.
 * 5. Guarda la huella en el atributo `visitFp` de la petición, marca la petición como
 *    autenticada en el `SecurityContext` y continúa.
 *
 * @param visitsService servicio que valida el token y extrae la huella.
 * @param limits servicio de límite de peticiones.
 * @param gateProperties reglas de acceso.
 * @param clientIpResolver obtiene la IP real del cliente detrás de los proxies.
 */
@Component
class VisitJwtFilter(
    private val visitsService: VisitsService,
    private val limits: VisitsRateLimitService,
    private val gateProperties: VisitGateProperties,
    private val clientIpResolver: ClientIpResolver,
) : OncePerRequestFilter() {

    private val matcher = AntPathMatcher()

    /**
     * Aplica las reglas de acceso a la petición. Ver la descripción de la clase.
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
        val path = req.requestURI
        val rule = gateProperties.rules.firstOrNull {
            it.pattern.isNotBlank() && matcher.match(it.pattern, path)
        }
        log.info("Gate ${req.method} $path rule=${rule?.pattern} auth=${rule?.auth} hasToken=${extractToken(req) != null}")
        if (rule == null || !rule.auth) {
            chain.doFilter(req, res)
            return
        }

        val token = extractToken(req)

        val fp = token?.let { visitsService.validateToken(it) }
        if (fp == null) {
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            res.contentType = "application/json"
            res.writer.write("""{"error":"visit_token_required","hint":"POST /visits/track"}""")
            return
        }

        val ip = clientIpResolver.resolve(req)
        val bucket = rule.pattern.trim('/').replace("/", "-").ifBlank { "root" }
        if (!limits.allow("rl:fp:$fp:$bucket", rule.fpPerMinute) ||
            !limits.allow("rl:ip:$ip:global", rule.ipPerMinute)
        ) {
            res.status = 429
            res.setHeader("Retry-After", "60")
            res.contentType = "application/json"
            res.writer.write("""{"error":"rate_limited"}""")
            return
        }

        req.setAttribute("visitFp", fp)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(fp, null, emptyList())
        chain.doFilter(req, res)
    }

    /**
     * Obtiene el token de visita de la petición. Si viene por los dos sitios, gana la cookie.
     *
     * @param req petición entrante.
     * @return el token, o `null` si no viene ni en la cookie `visit_jwt` ni en `Authorization: Bearer`.
     */
    private fun extractToken(req: HttpServletRequest): String? {
        val fromCookie = req.cookies?.firstOrNull { it.name == "visit_jwt" }?.value
        val fromHeader = req.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(" ")?.trim()
        return fromCookie ?: fromHeader
    }

    companion object {
        private val log: Logger = Logger.getLogger(VisitJwtFilter::class.java.name)
    }
}
