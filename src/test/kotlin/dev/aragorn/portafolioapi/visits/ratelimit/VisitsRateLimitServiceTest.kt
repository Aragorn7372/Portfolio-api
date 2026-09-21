package dev.aragorn.portafolioapi.visits.ratelimit

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class VisitsRateLimitServiceTest {
    @Mock
    private lateinit var redis: StringRedisTemplate

    @InjectMocks
    private lateinit var service: VisitsRateLimitService

    private val key = "rl:track:fp:1.2.3.4"
    private val maxPerMinute = 10
    private val ops: ValueOperations<String, String> = mock()

    private fun givenCount(count: Long?) {
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.increment(key)).thenReturn(count)
    }

    @Test
    @DisplayName("allow bien, primera vez permite y fija expiración")
    fun allowFirst() {
        givenCount(1L)

        val result = service.allow(key, maxPerMinute)

        assertTrue(result)
        verify(redis, times(1)).expire(key, Duration.ofMinutes(1))
    }

    @Test
    @DisplayName("allow bien, dentro del límite permite sin expirar")
    fun allowWithinLimit() {
        givenCount(5L)

        val result = service.allow(key, maxPerMinute)

        assertTrue(result)
        verify(redis, times(0)).expire(any<String>(), any<Duration>())
    }

    @Test
    @DisplayName("allow mal, sobre el límite deniega")
    fun allowExceeded() {
        givenCount(11L)

        val result = service.allow(key, maxPerMinute)

        assertFalse(result)
        verify(redis, times(0)).expire(any<String>(), any<Duration>())
    }

    @Test
    @DisplayName("allow mal, sin incremento deniega")
    fun allowNoIncrement() {
        givenCount(null)

        val result = service.allow(key, maxPerMinute)

        assertFalse(result)
        verify(redis, times(0)).expire(any<String>(), any<Duration>())
    }
}
