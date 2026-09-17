package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Cache as CaffeineCache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.core.RedisTemplate
import java.util.concurrent.ConcurrentHashMap
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import java.util.concurrent.Callable

class HybridCacheManager(
    private val redisCacheManager: RedisCacheManager,
    private val localCaffeine: CaffeineCache<Any, Any>,
    private val redisTemplate: RedisTemplate<String, String>
    ) : CacheManager {
    private val cacheMap= ConcurrentHashMap<String, Cache>()
    override fun getCache(name: String): Cache {
        return cacheMap.computeIfAbsent(name){
            cacheName -> val redisCache = redisCacheManager.getCache(cacheName)
            ?: throw IllegalArgumentException("No se pudo crear la caché de Redis")
            HybridCache(cacheName, localCaffeine, redisCache, redisTemplate)
        }
    }
    override fun getCacheNames(): Collection<String> = cacheMap.keys() as Collection<String>


    class HybridCache(
        private val cacheName: String,
        private val localCaffeine: CaffeineCache<Any, Any>,
        private val redisCache: Cache,
        private val redisTemplate: RedisTemplate<String, String>
    ):Cache {
        override fun getName(): String = cacheName
        override fun getNativeCache(): Any = this
        override fun get(key: Any): Cache.ValueWrapper?{
           return localCaffeine.getIfPresent(key)?.let{
               SimpleValueWrapper(it)
            }?: run{
              redisCache.get(key)?.let{
                  it.get()?.let{value ->
                      localCaffeine.put(key, value)
                      SimpleValueWrapper(value)
                  }
              }
            }
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
                localCaffeine.put(key, value)
                redisCache.put(key, value)
                redisTemplate.convertAndSend("cache:invalidate","$cacheName:$key")
            }
        }
        override fun evict(key: Any){
            localCaffeine.invalidate(key)
            redisCache.evict(key)
            redisTemplate.convertAndSend("cache:invalidate","$cacheName:$key")
        }
        fun clearLocalOnly(key: Any)=localCaffeine.invalidate(key)
        override fun clear() {
            localCaffeine.invalidateAll(); redisCache.clear()
        }
    }
}