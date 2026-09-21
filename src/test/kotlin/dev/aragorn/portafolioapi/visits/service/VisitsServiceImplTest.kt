package dev.aragorn.portafolioapi.visits.service

import dev.aragorn.portafolioapi.visits.dto.VisitResponseDto
import dev.aragorn.portafolioapi.visits.mapper.VisitMapper
import dev.aragorn.portafolioapi.visits.model.Visits
import dev.aragorn.portafolioapi.visits.repository.VisitsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class VisitsServiceImplTest {
    @Mock
    private lateinit var mapper: VisitMapper
    @Mock
    private lateinit var repositorio: VisitsRepository
    @Mock
    private lateinit var redis: StringRedisTemplate

    private val ops: ValueOperations<String, String> = mock()
    private val jwtSecret = "0123456789abcdef0123456789abcdef0123456789abcdef"
    private val jwtMinutes = 15L

    private lateinit var service: VisitsServiceImpl

    private val signals = TrackSignals(
        userAgent = "Mozilla/5.0",
        language = "es-ES",
        timezone = "Europe/Madrid",
        screen = "1920x1080",
        plugins = listOf("pdf", "x"),
    )
    private val ip = "1.2.3.4"
    private val visit7 = Visits(id = 1, total = 7)
    private val dto7 = VisitResponseDto(7)

    @BeforeEach
    fun setup() {
        service = VisitsServiceImpl(mapper, repositorio, redis, jwtSecret, jwtMinutes)
    }

    @Test
    fun get() = runTest {
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))
        whenever(mapper.toDto(visit7)).thenReturn(dto7)

        val result = service.get()

        assertEquals(dto7, result)
        verify(repositorio, times(1)).findById(1L)
        verify(mapper, times(1)).toDto(visit7)
    }

    @Test
    @DisplayName("get sin fila devuelve cero")
    fun getEmpty() = runTest {
        whenever(repositorio.findById(1L)).thenReturn(Optional.empty())
        whenever(mapper.toDto(Visits(id = 1))).thenReturn(VisitResponseDto(0))

        val result = service.get()

        assertEquals(VisitResponseDto(0), result)
        verify(mapper, times(1)).toDto(Visits(id = 1))
    }

    @Test
    fun total() = runTest {
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))

        val result = service.total()

        assertEquals(7L, result)
        verify(repositorio, times(1)).findById(1L)
    }

    @Test
    @DisplayName("total sin fila devuelve cero")
    fun totalEmpty() = runTest {
        whenever(repositorio.findById(1L)).thenReturn(Optional.empty())

        val result = service.total()

        assertEquals(0L, result)
    }

    @Test
    fun fingerprint() {
        val fp1 = service.fingerprint(signals, ip)
        val fp2 = service.fingerprint(signals, ip)

        assertEquals(fp1, fp2)
        assertEquals(64, fp1.length)
        assertNotEquals(fp1, service.fingerprint(signals, "5.6.7.8"))
    }

    @Test
    fun issueToken() {
        val fp = "abc123"

        val token = service.issueToken(fp)

        assertEquals(fp, service.validateToken(token))
    }

    @Test
    @DisplayName("token manipulado devuelve null")
    fun validateTokenBad() {
        val good = service.issueToken("abc123")
        val segments = good.split(".")
        // Cualquier byte efectivo alterado rompe la firma y se rechaza.
        // Nota: jjwt ignora 1 char extra al final de la firma (longitud % 4 == 1);
        // no otorga nada nuevo (mismo fp, misma expiración), así que no se endurece.
        val payloadTampered = segments[0] + "." + flipChar(segments[1]) + "." + segments[2]
        val signatureTampered = good.substringBeforeLast(".") + ".AAAA"

        assertNull(service.validateToken("basura"))
        assertNull(service.validateToken(payloadTampered))
        assertNull(service.validateToken(signatureTampered))
    }

    private fun flipChar(s: String): String {
        val replacement = if (s[0] != 'A') 'A' else 'B'
        return replacement + s.substring(1)
    }

    @Test
    @DisplayName("track con JWT válido no cuenta ni escribe")
    fun trackCounted() = runTest {
        val fp = service.fingerprint(signals, ip)
        val token = service.issueToken(fp)
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))

        val result = service.track(signals, ip, token)

        assertEquals(false, result.counted)
        assertEquals(7L, result.total)
        assertEquals(token, result.token)
        verify(repositorio, times(0)).save(any<Visits>())
        verify(redis, times(0)).opsForValue()
    }

    @Test
    @DisplayName("track con huella en Redis no cuenta y reemite")
    fun trackDuplicate() = runTest {
        val fp = service.fingerprint(signals, ip)
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get("visits:fp:$fp")).thenReturn("1")
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))

        val result = service.track(signals, ip, null)

        assertEquals(false, result.counted)
        assertEquals(7L, result.total)
        assertEquals(fp, service.validateToken(result.token))
        verify(repositorio, times(0)).save(any<Visits>())
    }

    @Test
    @DisplayName("track nuevo cuenta y fija ventana")
    fun trackNew() = runTest {
        val fp = service.fingerprint(signals, ip)
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get("visits:fp:$fp")).thenReturn(null)
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))
        whenever(repositorio.save(Visits(id = 1, total = 8))).thenReturn(Visits(id = 1, total = 8))
        whenever(ops.setIfAbsent("visits:fp:$fp", "1", Duration.ofMinutes(jwtMinutes))).thenReturn(true)

        val result = service.track(signals, ip, null)

        assertEquals(true, result.counted)
        assertEquals(8L, result.total)
        assertEquals(fp, service.validateToken(result.token))
        verify(repositorio, times(1)).save(Visits(id = 1, total = 8))
        verify(ops, times(1)).setIfAbsent("visits:fp:$fp", "1", Duration.ofMinutes(jwtMinutes))
    }

    @Test
    @DisplayName("track con choque optimista reintenta")
    fun trackRetry() = runTest {
        val fp = service.fingerprint(signals, ip)
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get("visits:fp:$fp")).thenReturn(null)
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))
        whenever(repositorio.save(any<Visits>()))
            .thenThrow(OptimisticLockingFailureException("choque"))
            .thenReturn(Visits(id = 1, total = 8))
        whenever(ops.setIfAbsent("visits:fp:$fp", "1", Duration.ofMinutes(jwtMinutes))).thenReturn(true)

        val result = service.track(signals, ip, null)

        assertEquals(true, result.counted)
        assertEquals(8L, result.total)
        verify(repositorio, times(2)).save(any<Visits>())
    }

    @Test
    @DisplayName("track agotados los intentos usa incremento atómico")
    fun trackExhausted() = runTest {
        val fp = service.fingerprint(signals, ip)
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get("visits:fp:$fp")).thenReturn(null)
        whenever(repositorio.findById(1L)).thenReturn(Optional.of(visit7))
        whenever(repositorio.save(any<Visits>())).thenThrow(OptimisticLockingFailureException("choque"))
        whenever(repositorio.increment()).thenReturn(1)
        whenever(ops.setIfAbsent("visits:fp:$fp", "1", Duration.ofMinutes(jwtMinutes))).thenReturn(true)

        val result = service.track(signals, ip, null)

        assertEquals(true, result.counted)
        assertEquals(7L, result.total)
        verify(repositorio, times(5)).save(any<Visits>())
        verify(repositorio, times(1)).increment()
    }
}
