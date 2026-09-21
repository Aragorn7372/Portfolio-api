package dev.aragorn.portafolioapi.visits.service

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import dev.aragorn.portafolioapi.visits.mapper.VisitMapper
import dev.aragorn.portafolioapi.visits.model.Visits
import dev.aragorn.portafolioapi.visits.repository.VisitsRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.logging.Logger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Date

@Service
class VisitsServiceImpl(
    private val mapper: VisitMapper,
    private val repository: VisitsRepository,
    private val redis: StringRedisTemplate,
    @Value($$"${app.visits.jwt-secret}")
    private val jwtSecret: String,
    @Value($$"${app.visits.jwt-minutes:15}")
    private val jwtMinutes: Long,
) : VisitsService {

    private val log: Logger = Logger.getLogger(VisitsServiceImpl::class.java.name)

    companion object {
        private const val MAX_INCREMENT_ATTEMPTS = 5
    }

    private fun signingKey() =
        Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    override suspend fun get(): VisitResponseDto = withContext(Dispatchers.IO) {
        mapper.toDto(repository.findById(1).orElse(Visits(id = 1)))
    }

    override suspend fun total(): Long = withContext(Dispatchers.IO) {
        repository.findById(1).map { it.total }.orElse(0L)
    }

    override fun fingerprint(signals: TrackSignals, ip: String): String {
        val raw = listOf(
            signals.userAgent.trim().lowercase(),
            signals.language.trim().lowercase(),
            signals.timezone.trim(),
            signals.screen.trim(),
            signals.plugins.map { it.trim().lowercase() }.sorted().joinToString(","),
            ip.trim(),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun issueToken(fingerprint: String): String {
        val now = Date()
        return Jwts.builder()
            .subject("visit")
            .claim("fp", fingerprint)
            .issuedAt(now)
            .expiration(Date(now.time + jwtMinutes * 60_000))
            .signWith(signingKey())
            .compact()
    }

    override fun validateToken(token: String): String? = runCatching {
        Jwts.parser().verifyWith(signingKey()).build()
            .parseSignedClaims(token).payload.get("fp", String::class.java)
    }.getOrNull()

    override suspend fun track(signals: TrackSignals, ip: String, incomingJwt: String?): TrackResult =
        withContext(Dispatchers.IO) {
            val fp = fingerprint(signals, ip)
            val redisKey = "visits:fp:$fp"

            if (incomingJwt != null && validateToken(incomingJwt) == fp) {
                return@withContext TrackResult(false, currentTotal(), incomingJwt)
            }
            if (redis.opsForValue().get(redisKey) != null) {
                return@withContext TrackResult(false, currentTotal(), issueToken(fp))
            }
            val total = incrementWithRetry()
            redis.opsForValue().setIfAbsent(redisKey, "1", Duration.ofMinutes(jwtMinutes))
            TrackResult(true, total, issueToken(fp))
        }

    private fun incrementWithRetry(): Long {
        repeat(MAX_INCREMENT_ATTEMPTS) { attempt ->
            try {
                val current = repository.findById(1).orElse(Visits(id = 1))
                val saved = repository.save(current.copy(total = current.total + 1))
                return saved.total
            } catch (_: OptimisticLockingFailureException) {
                log.warning("Choque optimista al incrementar visitas (intento ${attempt + 1}/$MAX_INCREMENT_ATTEMPTS), reintentando")
                if (attempt == MAX_INCREMENT_ATTEMPTS - 1) {
                    repository.increment()
                    return currentTotal()
                }
            }
        }
        error("Inalcanzable")
    }

    private fun currentTotal(): Long =
        repository.findById(1).map { it.total }.orElse(0L)
}
