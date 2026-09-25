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

/**
 * Implementación de [VisitsService]. El contador se guarda en PostgreSQL y la deduplicación en Redis.
 *
 * @param mapper conversor entidad → DTO.
 * @param repository repositorio del contador.
 * @param redis Redis, donde se guardan las huellas ya contadas (`visits:fp:<huella>`).
 * @param jwtSecret clave HMAC para firmar los tokens (`app.visits.jwt-secret`). Tiene que tener al
 *   menos 32 bytes (256 bits), o la librería JWT la rechaza.
 * @param jwtMinutes duración del token y de la ventana de deduplicación (`app.visits.jwt-minutes`, 15 por defecto).
 */
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
        /** Intentos de incremento con bloqueo optimista antes de pasar al `UPDATE` atómico. */
        private const val MAX_INCREMENT_ATTEMPTS = 5
    }

    /** Clave HMAC-SHA que se obtiene de [jwtSecret]. */
    private fun signingKey() =
        Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    override suspend fun get(): VisitResponseDto = withContext(Dispatchers.IO) {
        mapper.toDto(repository.findById(1).orElse(Visits(id = 1)))
    }

    override suspend fun total(): Long = withContext(Dispatchers.IO) {
        repository.findById(1).map { it.total }.orElse(0L)
    }

    /**
     * Antes de calcular el hash, normaliza las señales para que la huella sea estable: quita
     * espacios, pasa a minúsculas el user-agent, el idioma y los plugins, y ordena los plugins.
     * Después las une con `|` y calcula el SHA-256.
     */
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

    /**
     * Registra la visita con deduplicación en dos capas:
     *
     * 1. **Token del cliente:** si trae un token válido para esta misma huella, es una recarga. No
     *    se cuenta y se devuelve el mismo token.
     * 2. **Redis:** si la huella ya está en `visits:fp:<huella>` (por ejemplo, porque el cliente ha
     *    borrado las cookies), no se cuenta pero se emite un token nuevo.
     * 3. **Visita nueva:** se incrementa el contador (ver [incrementWithRetry]), se guarda la huella
     *    en Redis durante [jwtMinutes] minutos y se emite un token.
     */
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

    /**
     * Suma 1 al contador protegido por el bloqueo optimista de [Visits.version].
     *
     * Si otra instancia actualiza la fila a la vez, se reintenta hasta [MAX_INCREMENT_ATTEMPTS]
     * veces. En el último choque se usa [VisitsRepository.increment], un `UPDATE` atómico que no
     * puede fallar por concurrencia. Así la visita siempre se cuenta.
     *
     * @return el total después del incremento.
     */
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

    /** Lee el total actual de forma bloqueante, o `0` si todavía no existe el contador. */
    private fun currentTotal(): Long =
        repository.findById(1).map { it.total }.orElse(0L)
}
