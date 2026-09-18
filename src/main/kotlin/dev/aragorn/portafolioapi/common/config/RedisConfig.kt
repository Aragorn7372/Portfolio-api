package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class RedisConfig {
    @Value($$"${app.redis.certs.time:24}")
    private val certsTime: Long = 24
    @Value($$"${app.redis.projects.time:1}")
    private val projectsTime: Long = 1
    @Value($$"${app.redis.visits.time:1}")
    private val visitsTime: Long = 1

    @Bean
    fun cacheConfiguration(): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer.builder().build()
                )
            )
    @Bean
    fun cacheManager(
        redisConectionFactory: RedisConnectionFactory,
        baseconfig: RedisCacheConfiguration,
        redisTemplate: RedisTemplate<String, String>,
    ): CacheManager {
        val redisCacheConfiguration = mapOf(
            "certificados" to baseconfig.entryTtl(Duration.ofHours(certsTime)),
            "projects" to baseconfig.entryTtl(Duration.ofHours(projectsTime)),
            "visits" to baseconfig.entryTtl(Duration.ofMinutes(visitsTime)),
            )
        val redisCacheManager = RedisCacheManager.builder(redisConectionFactory)
            .cacheDefaults(baseconfig)
            .withInitialCacheConfigurations(redisCacheConfiguration)
            .build()
        val localCaffeine = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build<Any, Any>()
        return HybridCacheManager(redisCacheManager, localCaffeine,redisTemplate)
    }
    @Bean
    fun redisContainer(
        connectionFactory: RedisConnectionFactory,
        listenerAdapter: MessageListenerAdapter
    ): RedisMessageListenerContainer {
        return RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            addMessageListener(listenerAdapter, PatternTopic("cache:invalidate"))
        }
    }
    @Bean
    fun listenerAdapter(cacheManager: CacheManager): MessageListenerAdapter {
        return MessageListenerAdapter(object {
            fun handleMessage(message: String) {
                val parts = message.split(":", limit = 2)
                if (parts.size == 2) {
                    val cache= cacheManager.getCache(parts[0]) as? HybridCacheManager.HybridCache
                    cache?.clearLocalByStringKey(parts[1])
                }
            }
        }, "handleMessage")
    }
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {setConnectionFactory(connectionFactory)}
    }

}