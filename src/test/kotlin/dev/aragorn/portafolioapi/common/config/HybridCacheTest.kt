package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import org.springframework.data.redis.core.RedisTemplate
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
        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
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

    private fun hybridCache(cacheName: String, caffeine: com.github.benmanes.caffeine.cache.Cache<Any, Any>) =
        HybridCacheManager.HybridCache(cacheName, caffeine, MapCache(cacheName), NoOpRedisTemplate())

    @Test
    fun `misma key en certificados y projects no se mezclan`() {
        val caffeine = Caffeine.newBuilder().maximumSize(100).build<Any, Any>()
        val certificados = hybridCache("certificados", caffeine)
        val projects = hybridCache("projects", caffeine)
        val key = "SimpleKey []"

        certificados.put(key, listOf("cert-1"))
        projects.put(key, listOf("proj-1"))

        assertEquals(listOf("cert-1"), certificados.get(key)?.get())
        assertEquals(listOf("proj-1"), projects.get(key)?.get())
    }

    @Test
    fun `clear de una cache no vacia la otra`() {
        val caffeine = Caffeine.newBuilder().maximumSize(100).build<Any, Any>()
        val certificados = hybridCache("certificados", caffeine)
        val projects = hybridCache("projects", caffeine)
        val key = "SimpleKey []"

        certificados.put(key, listOf("cert-1"))
        projects.put(key, listOf("proj-1"))

        certificados.clear()

        assertNull(certificados.get(key))
        assertEquals(listOf("proj-1"), projects.get(key)?.get())
    }
}
