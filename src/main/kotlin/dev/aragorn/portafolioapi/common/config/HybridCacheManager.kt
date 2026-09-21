package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Cache as CaffeineCache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.ConcurrentHashMap
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import java.util.concurrent.Callable
import java.util.logging.Logger
import tools.jackson.databind.ObjectMapper

/**
 * Codec JSON manual por caché (opción B): Redis solo guarda Strings.
 * Así el lector no depende del serializador tipado de Spring y un valor
 * legacy (LinkedHashMap) nunca llega al controller: se descarta y se
 * recarga de DB (self-healing).
 */
interface CacheJsonCodec {
    fun serialize(value: Any): String
    fun deserialize(json: String): Any?
    fun isValid(value: Any?): Boolean
}

class JacksonListCodec(
    private val objectMapper: ObjectMapper,
    private val elementClass: Class<*>,
    private val cacheName: String,
) : CacheJsonCodec {
    private val listType =
        objectMapper.typeFactory.constructCollectionType(List::class.java, elementClass)

    override fun serialize(value: Any): String =
        objectMapper.writeValueAsString(value)

    override fun deserialize(json: String): Any? =
        objectMapper.readValue(json, listType)

    override fun isValid(value: Any?): Boolean =
        value is List<*> && value.all { it == null || elementClass.isInstance(it) }
}

class HybridCacheManager(
    private val redisCacheManager: RedisCacheManager,
    private val localCaffeine: CaffeineCache<Any, Any>,
    private val redisTemplate: RedisTemplate<String, String>,
    private val codecs: Map<String, CacheJsonCodec> = emptyMap(),
    ) : CacheManager {
    private val cacheMap= ConcurrentHashMap<String, Cache>()
    override fun getCache(name: String): Cache {
        return cacheMap.computeIfAbsent(name){
            cacheName -> val redisCache = redisCacheManager.getCache(cacheName)
            ?: throw IllegalArgumentException("No se pudo crear la caché de Redis")
            HybridCache(cacheName, localCaffeine, redisCache, redisTemplate, codecs[cacheName])
        }
    }
    override fun getCacheNames(): Collection<String> = cacheMap.keys.toSet()

    /** Clave compuesta: aisla la Caffeine compartida por nombre de caché. */
    private data class LocalKey(val cacheName: String, val key: Any)

    class HybridCache(
        private val cacheName: String,
        private val localCaffeine: CaffeineCache<Any, Any>,
        private val redisCache: Cache,
        private val redisTemplate: RedisTemplate<String, String>,
        private val codec: CacheJsonCodec? = null,
    ):Cache {
        override fun getName(): String = cacheName
        override fun getNativeCache(): Any = this
        private fun localKey(key: Any) = LocalKey(cacheName, key)

        private fun describeValue(value: Any?): String {
            if (value == null) return "null"
            val first = (value as? List<*>)?.firstOrNull()
            return "${value.javaClass.name} element=${first?.javaClass?.name}"
        }

        override fun get(key: Any): Cache.ValueWrapper?{
           return localCaffeine.getIfPresent(localKey(key))?.let{
               SimpleValueWrapper(it)
            }?: run{
              val raw = try {
                  redisCache.get(key)?.get()
              } catch (ex: Exception) {
                  log.severe("HybridCache[$cacheName] Redis GET FAILED key=$key error=${ex.message}")
                  null
              } ?: return@run null
              val value = if (codec != null) decodeAnHeal(key, raw, codec) else raw
              if (value == null) return@run null
              localCaffeine.put(localKey(key), value)
              SimpleValueWrapper(value)
            }
        }

        /** Deserializa con el codec; ante veneno legacy devuelve null y evicta (self-healing). */
        private fun decodeAnHeal(key: Any, raw: Any?, codec: CacheJsonCodec): Any? {
            if (raw !is String) {
                log.severe("HybridCache[$cacheName] POISON key=$key raw=${describeValue(raw)} (no es String), evictando")
                evictQuietly(key)
                return null
            }
            val parsed = try {
                codec.deserialize(raw)
            } catch (ex: Exception) {
                log.severe("HybridCache[$cacheName] DESERIALIZE FAILED key=$key error=${ex.message}, evictando")
                evictQuietly(key)
                return null
            }
            if (!codec.isValid(parsed)) {
                log.severe("HybridCache[$cacheName] POISON key=$key parsed=${describeValue(parsed)}, evictando")
                evictQuietly(key)
                return null
            }
            log.info("HybridCache[$cacheName] Redis HIT key=$key value=${describeValue(parsed)}")
            return parsed
        }

        private fun evictQuietly(key: Any) {
            runCatching { localCaffeine.invalidate(localKey(key)) }
            runCatching { redisCache.evict(key) }
        }

        override fun <T : Any> get(key: Any, type: Class<T>?): T? {
            val wrapper = get(key)
            @Suppress("UNCHECKED_CAST") return wrapper?.get() as T?
        }

        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
            val wrapper = get(key)?.let{
                @Suppress("UNCHECKED_CAST") return it.get() as T
            }
            val value= valueLoader.call()
            put(key, value)
            return value
        }

        override fun put(key: Any, value: Any?) {
            if (value!=null){
                log.info("HybridCache[$cacheName] PUT key=$key value=${describeValue(value)}")
                localCaffeine.put(localKey(key), value)
                // La caché es optimización: si Redis falla, la petición sigue con el valor local.
                try {
                    val toStore: Any = codec?.serialize(value) ?: value
                    redisCache.put(key, toStore)
                } catch (ex: Exception) {
                    log.severe("HybridCache[$cacheName] Redis PUT FAILED key=$key value=${describeValue(value)} error=${ex.message}")
                    var cause = ex.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        log.severe("HybridCache Caused by [$depth] ${cause.javaClass.name}: ${cause.message}")
                        cause = cause.cause
                        depth++
                    }
                }
                runCatching { redisTemplate.convertAndSend("cache:invalidate","$cacheName:$key") }
            }
        }
        override fun evict(key: Any){
            localCaffeine.invalidate(localKey(key))
            redisCache.evict(key)
            redisTemplate.convertAndSend("cache:invalidate","$cacheName:$key")
        }
        fun clearLocalOnly(key: Any)=localCaffeine.invalidate(localKey(key))

        /** Invalidación remota: solo llega "$cacheName:keyComoString", sin el objeto original. */
        fun clearLocalByStringKey(keyAsString: String) {
            localCaffeine.asMap().keys
                .filterIsInstance<LocalKey>()
                .filter { it.cacheName == cacheName && it.key.toString() == keyAsString }
                .forEach { localCaffeine.invalidate(it) }
        }
        override fun clear() {
            localCaffeine.asMap().keys
                .filterIsInstance<LocalKey>()
                .filter { it.cacheName == cacheName }
                .forEach { localCaffeine.invalidate(it) }
            redisCache.clear()
        }

        companion object {
            private val log: Logger = Logger.getLogger(HybridCache::class.java.name)
        }
    }
}