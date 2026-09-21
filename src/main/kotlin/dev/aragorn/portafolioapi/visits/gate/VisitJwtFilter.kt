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

@Component
class VisitJwtFilter(
    private val visitsService: VisitsService,
    private val limits: VisitsRateLimitService,
    private val gateProperties: VisitGateProperties,
) : OncePerRequestFilter() {

    private val matcher = AntPathMatcher()

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

        val ip = req.remoteAddr ?: "unknown"
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
