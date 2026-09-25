package dev.aragorn.portafolioapi.visits.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Límite de peticiones de ventana fija de un minuto, guardado en Redis.
 *
 * Cada clave es un contador que se incrementa de forma atómica (`INCR`). Con el primer
 * incremento se le pone una caducidad de 60 s. Como el estado está en Redis, el límite es el
 * mismo para todas las instancias de la API.
 *
 * Al ser de ventana fija, un cliente puede llegar a hacer hasta el doble del límite si reparte las
 * peticiones entre el final de una ventana y el principio de la siguiente.
 *
 * @param redis plantilla de Redis con claves y valores de texto.
 */
@Service
class VisitsRateLimitService(private val redis: StringRedisTemplate){
    /**
     * Registra una petición y dice si todavía está dentro del límite.
     *
     * Las peticiones rechazadas también cuentan, así que un cliente que insiste no libera cupo
     * hasta que cambia la ventana.
     *
     * @param key clave del contador. Por convención empieza por `rl:`, por ejemplo `rl:ip:<ip>:global`.
     * @param maxPerMinute peticiones permitidas por ventana.
     * @return `true` si la petición está permitida. `false` si se ha superado el límite o Redis no
     *   ha devuelto valor (en ese caso se rechaza por seguridad).
     */
    fun allow(key: String, maxPerMinute: Int): Boolean{
        val count=redis.opsForValue().increment(key) ?: return false
        if( count == 1L) redis.expire(key, Duration.ofMinutes(1))
        return count <= maxPerMinute
    }
}