package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.core.RedisTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

class HybridCacheTest {

    private class MapCache(private val name: String) : Cache {
        private val store = ConcurrentHashMap<Any, Any>()
        override fun getName(): String = name
        override fun getNativeCache(): Any = store
        override fun get(key: Any): Cache.ValueWrapper? = store[key]?.let { SimpleValueWrapper(it) }
        override fun <T : Any> get(key: Any, type: Class<T>?): T? {
            @Suppress("UNCHECKED_CAST")
            return store[key] as T?
        }
        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T {
            @Suppress("UNCHECKED_CAST")
            return store.computeIfAbsent(key) { valueLoader.call() as Any } as T
        }
        override fun put(key: Any, value: Any?) {
            if (value != null) store[key] = value
        }
        override fun evict(key: Any) {
            store.remove(key)
        }
        override fun clear() = store.clear()
    }

    private class NoOpRedisTemplate : RedisTemplate<String, String>() {
        override fun convertAndSend(channel: String, message: Any): Long = 0L
    }

    private class ThrowingCache(cause: Throwable? = null) : Cache {
        private val failure = RuntimeException("redis down", cause)
        override fun getName(): String = "throwing"
        override fun getNativeCache(): Any = this
        override fun get(key: Any): Cache.ValueWrapper = throw failure
        override fun <T : Any> get(key: Any, type: Class<T>?): T = throw failure
        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T = throw failure
        override fun put(key: Any, value: Any?) = throw failure
        override fun evict(key: Any) = Unit
        override fun clear() = Unit
    }

    private class FakeCodec(private val parsed: Any?) : CacheJsonCodec {
        override fun serialize(value: Any): String = "fake"
        override fun deserialize(json: String): Any? = parsed
        override fun isValid(value: Any?): Boolean =
            value is List<*> && value.all { it is CertificatesResponseDto }
    }

    private fun newCaffeine() = Caffeine.newBuilder().maximumSize(100).build<Any, Any>()

    private fun hybridCache(cacheName: String, caffeine: com.github.benmanes.caffeine.cache.Cache<Any, Any>) =
        HybridCacheManager.HybridCache(cacheName, caffeine, MapCache(cacheName), NoOpRedisTemplate())

    private fun certsCodec() =
        JacksonListCodec(
            JsonMapper.builder().addModule(KotlinModule.Builder().build()).build(),
            CertificatesResponseDto::class.java,
            "certificados",
        )

    private val dtos1 = listOf(
        CertificatesResponseDto("t1", "https://drive.google.com/file/d/abc", "2024-01-02"),
    )

    @Test
    @DisplayName("misma key en certificados y projects no se mezclan")
    fun cacheIsolation() {
        val caffeine = newCaffeine()
        val certificados = hybridCache("certificados", caffeine)
        val projects = hybridCache("projects", caffeine)
        val key = "SimpleKey []"

        certificados.put(key, listOf("cert-1"))
        projects.put(key, listOf("proj-1"))

        assertEquals(listOf("cert-1"), certificados.get(key)?.get())
        assertEquals(listOf("proj-1"), projects.get(key)?.get())
    }

    @Test
    @DisplayName("clear de una cache no vacia la otra")
    fun clearIsolation() {
        val caffeine = newCaffeine()
        val certificados = hybridCache("certificados", caffeine)
        val projects = hybridCache("projects", caffeine)
        val key = "SimpleKey []"

        certificados.put(key, listOf("cert-1"))
        projects.put(key, listOf("proj-1"))

        certificados.clear()

        assertNull(certificados.get(key))
        assertEquals(listOf("proj-1"), projects.get(key)?.get())
    }

    @Test
    @DisplayName("roundtrip via codec conserva DTOs tras caducar caffeine")
    fun codecRoundtrip() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        val cache = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate(), certsCodec())

        cache.put("k", dtos1)
        // Redis solo debe guardar Strings (opción B)
        assertEquals(true, (store.get("k")?.get() is String))
        // Simula caducidad de Caffeine: el HIT va a Redis y debe devolver DTOs, no maps
        caffeine.invalidateAll()

        assertEquals(dtos1, cache.get("k")?.get())
    }

    @Test
    @DisplayName("veneno legacy se descarta y hace miss")
    fun poisonLegacy() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        // Veneno legacy: objeto (no String) escrito por el serializador antiguo
        store.put("k", listOf(linkedMapOf("titulo" to "x", "url" to "y", "fecha" to "2024-01-02")))
        val cache = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate(), certsCodec())

        assertNull(cache.get("k"))
        // Self-healing: la key envenenada se evicta para recargar de DB
        assertNull(store.get("k"))
    }

    @Test
    @DisplayName("get tipado devuelve hit y miss")
    fun getTyped() {
        val caffeine = newCaffeine()
        val cache = hybridCache("certificados", caffeine)

        cache.put("k", listOf("v"))

        assertEquals(listOf("v"), cache.get("k", List::class.java))
        assertNull(cache.get("otra", List::class.java))
    }

    @Test
    @DisplayName("get con loader solo carga en miss")
    fun getWithLoader() {
        val caffeine = newCaffeine()
        val cache = hybridCache("certificados", caffeine)
        var calls = 0

        val first: List<String>? = cache.get("k", Callable { calls++; listOf("v") })
        val second: List<String>? = cache.get("k", Callable { calls++; listOf("otro") })

        assertEquals(listOf("v"), first)
        assertEquals(listOf("v"), second)
        assertEquals(1, calls)
    }

    @Test
    @DisplayName("put null se ignora")
    fun putNull() {
        val caffeine = newCaffeine()
        val cache = hybridCache("certificados", caffeine)

        cache.put("k", null)

        assertNull(cache.get("k"))
    }

    @Test
    @DisplayName("put con redis caído aguanta en local")
    fun putRedisDown() {
        val caffeine = newCaffeine()
        val cache = HybridCacheManager.HybridCache(
            "certificados", caffeine,
            ThrowingCache(IllegalStateException("causa")), NoOpRedisTemplate()
        )

        assertDoesNotThrow {
            cache.put("k", listOf("v"))
        }

        assertEquals(listOf("v"), cache.get("k")?.get())
    }

    @Test
    @DisplayName("get con redis caído hace miss")
    fun getRedisDown() {
        val caffeine = newCaffeine()
        val cache = HybridCacheManager.HybridCache(
            "certificados", caffeine, ThrowingCache(), NoOpRedisTemplate()
        )

        assertNull(cache.get("k"))
    }

    @Test
    @DisplayName("json inválido se descarta y hace miss")
    fun deserializeFailed() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        store.put("k", "no-json{{{")
        val cache = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate(), certsCodec())

        assertNull(cache.get("k"))
        assertNull(store.get("k"))
    }

    @Test
    @DisplayName("parseado inválido se descarta y hace miss")
    fun parsedInvalid() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        store.put("k1", "{}")
        store.put("k2", "{}")
        val cache1 = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate(), FakeCodec(listOf("no-dto")))
        val cache2 = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate(), FakeCodec(null))

        assertNull(cache1.get("k1"))
        assertNull(store.get("k1"))
        assertNull(cache2.get("k2"))
        assertNull(store.get("k2"))
    }

    @Test
    @DisplayName("evict limpia local y redis")
    fun evict() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        val cache = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate())

        cache.put("k", listOf("v"))
        cache.evict("k")

        assertNull(cache.get("k"))
        assertNull(store.get("k"))
    }

    @Test
    @DisplayName("clearLocalOnly recarga de redis")
    fun clearLocalOnly() {
        val caffeine = newCaffeine()
        val store = MapCache("certificados")
        val cache = HybridCacheManager.HybridCache("certificados", caffeine, store, NoOpRedisTemplate())

        cache.put("k", listOf("v"))
        cache.clearLocalOnly("k")

        assertEquals(listOf("v"), cache.get("k")?.get())
    }

    @Test
    @DisplayName("clearLocalByStringKey solo limpia esa key")
    fun clearLocalByStringKey() {
        val caffeine = newCaffeine()
        val cache = hybridCache("certificados", caffeine)

        cache.put("k1", listOf("a"))
        cache.put("k2", listOf("b"))
        cache.clearLocalByStringKey("k2")

        assertEquals(listOf("a"), cache.get("k1")?.get())
        assertEquals(listOf("b"), cache.get("k2")?.get())
    }

    @Test
    @DisplayName("manager cachea por nombre y falla si falta redis")
    fun managerCaches() {
        val caffeine = newCaffeine()
        val redisManager: RedisCacheManager = mock()
        whenever(redisManager.getCache("certificados")).thenReturn(MapCache("certificados"))
        whenever(redisManager.getCache("otra")).thenReturn(null)
        val manager = HybridCacheManager(redisManager, caffeine, NoOpRedisTemplate(), mapOf("certificados" to certsCodec()))

        val first = manager.getCache("certificados")
        val second = manager.getCache("certificados")

        assertSame(first, second)
        assertEquals("certificados", first.name)
        assertTrue(manager.cacheNames.contains("certificados"))

        val exception = assertThrows<IllegalArgumentException> {
            manager.getCache("otra")
        }
        assertEquals("No se pudo crear la caché de Redis", exception.message)
    }

    @Test
    @DisplayName("codec serializa, deserializa y valida")
    fun codecDirect() {
        val codec = certsCodec()

        val json = codec.serialize(dtos1)

        assertTrue(json.isNotBlank())
        assertEquals(dtos1, codec.deserialize(json))
        assertTrue(codec.isValid(dtos1))
        assertFalse(codec.isValid(listOf("no-dto")))
        assertFalse(codec.isValid(null))
        assertFalse(codec.isValid("texto"))
    }
}
