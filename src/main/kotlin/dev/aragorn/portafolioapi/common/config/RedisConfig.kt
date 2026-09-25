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

/**
 * Configuración de la caché de la aplicación y de la mensajería Redis que la mantiene coherente.
 *
 * Activa `@EnableCaching` y registra un [HybridCacheManager] (Caffeine + Redis) como
 * [CacheManager] principal. Cachés definidas:
 *
 * | Caché          | TTL en Redis                         | Valor en Redis | Códec                      |
 * |----------------|--------------------------------------|----------------|----------------------------|
 * | `certificados` | `app.redis.certs.time` horas (24)    | JSON (`String`)| [JacksonListCodec]         |
 * | `projects`     | `app.redis.projects.time` horas (1)  | JSON (`String`)| [JacksonListCodec]         |
 * | `visits`       | `app.redis.visits.time` minutos (1)  | JSON genérico  | ninguno                    |
 * | cualquier otra | 5 minutos                            | JSON genérico  | ninguno                    |
 *
 * La L1 en memoria caduca 1 minuto después de escribirse y guarda como mucho 1000 entradas en
 * total, así que aunque se pierda un mensaje de invalidación, una instancia no sirve datos
 * antiguos durante más de un minuto.
 *
 * También registra el listener del canal `cache:invalidate`, que limpia la L1 de esta instancia
 * cuando otra instancia escribe o borra una clave.
 */
@Configuration
@EnableCaching
class RedisConfig {
    /** TTL en horas de la caché `certificados` (`app.redis.certs.time`, 24 por defecto). */
    @Value($$"${app.redis.certs.time:24}")
    private val certsTime: Long = 24

    /** TTL en horas de la caché `projects` (`app.redis.projects.time`, 1 por defecto). */
    @Value($$"${app.redis.projects.time:1}")
    private val projectsTime: Long = 1

    /** TTL en minutos de la caché `visits` (`app.redis.visits.time`, 1 por defecto). */
    @Value($$"${app.redis.visits.time:1}")
    private val visitsTime: Long = 1

    /**
     * Configuración base de Redis para las cachés sin configuración propia: TTL de 5 minutos y
     * valores serializados como JSON genérico con Jackson.
     *
     * @return la configuración por defecto que heredan todas las cachés.
     */
    @Bean
    fun cacheConfiguration(): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJacksonJsonRedisSerializer.builder().build()
                )
            )

    /**
     * Construye el [HybridCacheManager] que usan todas las anotaciones `@Cacheable` / `@CacheEvict`.
     *
     * `certificados` y `projects` guardan en Redis un `String` JSON y usan un [JacksonListCodec],
     * así al leerlas los elementos vuelven como DTOs reales y no como mapas. Al arrancar se
     * registra qué `ObjectMapper` se usa y si el módulo de Kotlin de Jackson está en el
     * classpath, para diagnosticar problemas de deserialización.
     *
     * @param redisConectionFactory conexión a Redis.
     * @param baseconfig configuración por defecto de [cacheConfiguration].
     * @param redisTemplate plantilla para publicar invalidaciones.
     * @param objectMapper mapper de Jackson de la aplicación.
     * @return el gestor de caché híbrido.
     */
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

    /**
     * Contenedor que escucha el canal `cache:invalidate` y reparte sus mensajes a [listenerAdapter].
     *
     * @param connectionFactory conexión a Redis.
     * @param listenerAdapter adaptador que procesa cada mensaje.
     * @return el contenedor de listeners ya suscrito.
     */
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

    /**
     * Procesa los mensajes de invalidación de caché que publican las instancias.
     *
     * Cada mensaje tiene el formato `"<caché>:<clave>"`. Se separa por el primer `:` y se llama a
     * [HybridCacheManager.HybridCache.clearLocalByStringKey] para borrar solo la copia L1 de esta
     * instancia (L2 ya está actualizada). Los mensajes con otro formato se ignoran.
     *
     * @param cacheManager gestor de caché de la aplicación.
     * @return adaptador que invoca `handleMessage(String)` por cada mensaje.
     */
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

    /**
     * Plantilla `String`/`String` que se usa para publicar en `cache:invalidate`.
     *
     * @param connectionFactory conexión a Redis.
     * @return la plantilla configurada.
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {setConnectionFactory(connectionFactory)}
    }

    companion object {
        private val log: Logger = Logger.getLogger(RedisConfig::class.java.name)
    }

}