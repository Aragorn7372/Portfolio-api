package dev.aragorn.portafolioapi.visits.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class VisitsRateLimitService(private val redis: StringRedisTemplate){
    fun allow(key: String, maxPerMinute: Int): Boolean{
        val count=redis.opsForValue().increment(key) ?: return false
        if( count == 1L) redis.expire(key, Duration.ofMinutes(1))
        return count <= maxPerMinute
    }
}