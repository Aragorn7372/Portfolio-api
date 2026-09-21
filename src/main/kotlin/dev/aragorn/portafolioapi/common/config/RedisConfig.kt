package dev.aragorn.portafolioapi.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
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
import org.springframework.data.redis.serializer.StringRedisSerializer
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

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
        objectMapper: ObjectMapper,
    ): CacheManager {
        val kotlinModulePresent = runCatching { Class.forName("tools.jackson.module.kotlin.KotlinModule") }.isSuccess
        log.info("Redis ObjectMapper class=${objectMapper.javaClass.name} kotlinModuleOnClasspath=$kotlinModulePresent")
        val stringPair = RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
        val redisCacheConfiguration = mapOf(
            "certificados" to baseconfig.entryTtl(Duration.ofHours(certsTime))
                .serializeValuesWith(stringPair),
            "projects" to baseconfig.entryTtl(Duration.ofHours(projectsTime))
                .serializeValuesWith(stringPair),
            "visits" to baseconfig.entryTtl(Duration.ofMinutes(visitsTime)),
            )
        val codecs: Map<String, CacheJsonCodec> = mapOf(
            "certificados" to JacksonListCodec(objectMapper, CertificatesResponseDto::class.java, "certificados"),
            "projects" to JacksonListCodec(objectMapper, ProjectResponseDto::class.java, "projects"),
        )
        val redisCacheManager = RedisCacheManager.builder(redisConectionFactory)
            .cacheDefaults(baseconfig)
            .withInitialCacheConfigurations(redisCacheConfiguration)
            .build()
        val localCaffeine = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build<Any, Any>()
        return HybridCacheManager(redisCacheManager, localCaffeine,redisTemplate, codecs)
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

    companion object {
        private val log: Logger = Logger.getLogger(RedisConfig::class.java.name)
    }

}