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
 * Estrategia de (de)serialización JSON para los valores que se guardan en el nivel Redis de
 * [HybridCacheManager.HybridCache].
 *
 * Existe porque el serializador genérico de Redis pierde el tipo concreto de los elementos
 * de una lista al leerla (devuelve mapas en lugar de DTOs). Con un códec, cada caché guarda
 * un `String` JSON y sabe reconstruir exactamente el tipo que espera.
 *
 * @see JacksonListCodec
 */
interface CacheJsonCodec {
    /**
     * Convierte el valor a JSON antes de guardarlo en Redis.
     *
     * @param value valor no nulo que devolvió el método cacheado.
     * @return representación JSON del valor.
     */
    fun serialize(value: Any): String

    /**
     * Reconstruye el valor a partir del JSON leído de Redis.
     *
     * @param json cadena guardada previamente con [serialize].
     * @return el valor reconstruido, o `null` si el JSON representa un nulo.
     * @throws Exception si el JSON está corrupto o no encaja con el tipo esperado.
     */
    fun deserialize(json: String): Any?

    /**
     * Comprueba que un valor deserializado tiene el tipo que la caché espera.
     *
     * Se usa para detectar entradas "envenenadas": JSON válido pero con otra forma, por
     * ejemplo el que dejó una versión anterior de la aplicación con otro DTO.
     *
     * @param value valor devuelto por [deserialize].
     * @return `true` si el valor se puede devolver con seguridad al código que llama.
     */
    fun isValid(value: Any?): Boolean
}

/**
 * [CacheJsonCodec] para cachés cuyo valor es una `List<T>` de un DTO concreto.
 *
 * Construye con Jackson el tipo parametrizado `List<elementClass>` una sola vez y lo usa en
 * cada lectura, así los elementos vuelven como instancias reales de [elementClass] y no como
 * mapas genéricos.
 *
 * @param objectMapper mapper de Jackson de la aplicación (con el módulo de Kotlin).
 * @param elementClass clase de cada elemento de la lista (p. ej. `ProjectResponseDto`).
 * @param cacheName nombre de la caché que usa este códec. Sirve de referencia para diagnóstico.
 */
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

    /** Es válido si es una lista y todos sus elementos no nulos son instancias de [elementClass]. */
    override fun isValid(value: Any?): Boolean =
        value is List<*> && value.all { it == null || elementClass.isInstance(it) }
}

/**
 * [CacheManager] de dos niveles: una caché local en memoria (Caffeine, L1) delante de una
 * caché distribuida en Redis (L2).
 *
 * ## Cómo funciona
 * - **Lectura:** primero se busca en L1. Si no está, se lee de L2 y, si existe, se copia a L1
 *   para las siguientes lecturas.
 * - **Escritura:** se guarda en L1 y en L2, y se publica un mensaje en el canal Redis
 *   `cache:invalidate` con el formato `"<caché>:<clave>"`, para que las demás instancias
 *   descarten su copia local y no sirvan datos antiguos.
 * - **Tolerancia a fallos:** si Redis no responde, las lecturas y escrituras se degradan a
 *   solo L1 y el error queda en el log. La petición no falla por un problema de caché.
 *
 * Todas las cachés comparten la misma instancia de Caffeine. Para que no choquen claves
 * iguales de cachés distintas, cada clave local se envuelve en un [LocalKey] con el nombre
 * de la caché.
 *
 * La instancia se crea en [RedisConfig.cacheManager], que también decide qué cachés usan un
 * [CacheJsonCodec].
 *
 * @param redisCacheManager gestor de Redis que proporciona la caché L2 de cada nombre, con su TTL.
 * @param localCaffeine caché en memoria compartida que hace de L1.
 * @param redisTemplate plantilla con la que se publican los mensajes de invalidación.
 * @param codecs códec por nombre de caché. Las cachés sin códec guardan el valor tal cual.
 */
class HybridCacheManager(
    private val redisCacheManager: RedisCacheManager,
    private val localCaffeine: CaffeineCache<Any, Any>,
    private val redisTemplate: RedisTemplate<String, String>,
    private val codecs: Map<String, CacheJsonCodec> = emptyMap(),
    ) : CacheManager {
    private val cacheMap= ConcurrentHashMap<String, Cache>()

    /**
     * Devuelve la [HybridCache] del nombre pedido y la crea la primera vez que se solicita.
     *
     * @param name nombre de la caché, tal y como aparece en `@Cacheable(cacheNames = [...])`.
     * @return la caché híbrida asociada a ese nombre. Siempre es la misma instancia.
     * @throws IllegalArgumentException si Redis no puede crear la caché L2 de ese nombre.
     */
    override fun getCache(name: String): Cache {
        return cacheMap.computeIfAbsent(name){
            cacheName -> val redisCache = redisCacheManager.getCache(cacheName)
            ?: throw IllegalArgumentException("No se pudo crear la caché de Redis")
            HybridCache(cacheName, localCaffeine, redisCache, redisTemplate, codecs[cacheName])
        }
    }
    /** Nombres de las cachés que se han creado hasta ahora. */
    override fun getCacheNames(): Collection<String> = cacheMap.keys.toSet()

    /**
     * Clave compuesta en L1 para aislar las entradas de cada caché dentro del Caffeine compartido.
     *
     * @property cacheName nombre de la caché propietaria de la entrada.
     * @property key clave original que genera Spring Cache.
     */
    private data class LocalKey(val cacheName: String, val key: Any)

    /**
     * Caché individual de dos niveles (L1 en memoria + L2 en Redis). Ver [HybridCacheManager]
     * para la visión general.
     *
     * Si tiene un [codec], los valores se guardan en Redis como JSON y al leerlos se validan.
     * Una entrada corrupta o con un tipo inesperado se considera "envenenada": se borra de
     * ambos niveles y se trata como un fallo de caché, así el método cacheado vuelve a
     * calcular el valor en lugar de devolver datos rotos.
     *
     * @param cacheName nombre lógico de la caché.
     * @param localCaffeine caché L1 compartida.
     * @param redisCache caché L2 de este nombre, con su TTL propio.
     * @param redisTemplate plantilla para publicar invalidaciones.
     * @param codec códec opcional para serializar y validar los valores en L2.
     */
    class HybridCache(
        private val cacheName: String,
        private val localCaffeine: CaffeineCache<Any, Any>,
        private val redisCache: Cache,
        private val redisTemplate: RedisTemplate<String, String>,
        private val codec: CacheJsonCodec? = null,
    ):Cache {
        /** Nombre lógico de la caché. */
        override fun getName(): String = cacheName

        /** Devuelve la propia caché híbrida, ya que no hay un único objeto nativo por debajo. */
        override fun getNativeCache(): Any = this
        private fun localKey(key: Any) = LocalKey(cacheName, key)

        /** Descripción corta del tipo de un valor (y de su primer elemento si es lista) para los logs. */
        private fun describeValue(value: Any?): String {
            if (value == null) return "null"
            val first = (value as? List<*>)?.firstOrNull()
            return "${value.javaClass.name} element=${first?.javaClass?.name}"
        }

        /**
         * Busca un valor primero en L1 y después en L2.
         *
         * Si lo encuentra en L2, lo decodifica con el [codec] (si hay) y lo copia a L1.
         * Un error de Redis se registra y se trata como fallo de caché, sin propagarlo.
         *
         * @param key clave generada por Spring Cache.
         * @return el valor envuelto, o `null` si no está en ninguno de los dos niveles o era inválido.
         */
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

        /**
         * Decodifica un valor leído de Redis y "cura" la caché si está envenenado.
         *
         * Se descarta la entrada (se borra de L1 y L2 y se devuelve `null`) en tres casos:
         * 1. El valor crudo no es un `String` (lo escribió un serializador distinto).
         * 2. El JSON no se puede deserializar.
         * 3. El resultado no supera [CacheJsonCodec.isValid].
         *
         * @param key clave de la entrada.
         * @param raw valor tal cual lo devolvió Redis.
         * @param codec códec de esta caché.
         * @return el valor decodificado, o `null` si se descartó.
         */
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

        /** Borra la clave de L1 y L2 ignorando cualquier error. No publica invalidación. */
        private fun evictQuietly(key: Any) {
            runCatching { localCaffeine.invalidate(localKey(key)) }
            runCatching { redisCache.evict(key) }
        }

        /**
         * Variante tipada de [get]. No comprueba el tipo: hace un cast sin verificar.
         *
         * @param key clave de la entrada.
         * @param type tipo esperado (no se usa para validar).
         * @return el valor, o `null` si no está en caché.
         */
        override fun <T : Any> get(key: Any, type: Class<T>?): T? {
            val wrapper = get(key)
            @Suppress("UNCHECKED_CAST") return wrapper?.get() as T?
        }

        /**
         * Devuelve el valor en caché o, si no está, lo calcula con [valueLoader] y lo guarda.
         *
         * No sincroniza entre hilos: si dos llamadas fallan a la vez, las dos ejecutan el loader.
         *
         * @param key clave de la entrada.
         * @param valueLoader función que calcula el valor cuando no está en caché.
         * @return el valor en caché o el recién calculado.
         */
        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
            val wrapper = get(key)?.let{
                @Suppress("UNCHECKED_CAST") return it.get() as T
            }
            val value= valueLoader.call()
            put(key, value)
            return value
        }

        /**
         * Guarda un valor en L1 y L2 y avisa a las demás instancias por `cache:invalidate`.
         *
         * - Los valores `null` se ignoran: no se cachean.
         * - Si hay [codec], en Redis se guarda el JSON y no el objeto.
         * - Si Redis falla, el valor se queda solo en L1 y el error (con hasta 5 causas) va al log.
         *
         * @param key clave de la entrada.
         * @param value valor a cachear.
         */
        override fun put(key: Any, value: Any?) {
            if (value!=null){
                log.info("HybridCache[$cacheName] PUT key=$key value=${describeValue(value)}")
                localCaffeine.put(localKey(key), value)
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
        /**
         * Borra una clave de L1 y L2 y publica la invalidación para el resto de instancias.
         *
         * A diferencia de [put], aquí los errores de Redis sí se propagan.
         *
         * @param key clave a eliminar.
         */
        override fun evict(key: Any){
            localCaffeine.invalidate(localKey(key))
            redisCache.evict(key)
            redisTemplate.convertAndSend("cache:invalidate","$cacheName:$key")
        }

        /**
         * Borra una clave solo de L1, sin tocar Redis ni avisar a otras instancias.
         *
         * @param key clave a eliminar de la memoria local.
         */
        fun clearLocalOnly(key: Any)=localCaffeine.invalidate(localKey(key))

        /**
         * Borra de L1 las entradas de esta caché cuya clave, pasada a texto, coincide con [keyAsString].
         *
         * Lo usa el listener de `cache:invalidate` ([RedisConfig.listenerAdapter]): el mensaje solo
         * trae la clave en texto, así que se compara con `toString()` de cada clave local.
         *
         * @param keyAsString clave recibida en el mensaje de invalidación.
         */
        fun clearLocalByStringKey(keyAsString: String) {
            localCaffeine.asMap().keys
                .filterIsInstance<LocalKey>()
                .filter { it.cacheName == cacheName && it.key.toString() == keyAsString }
                .forEach { localCaffeine.invalidate(it) }
        }
        /**
         * Vacía la caché completa: sus entradas de L1 (sin tocar las de otras cachés) y toda su L2.
         *
         * No publica invalidación, así que las demás instancias conservan su L1 hasta que caduque.
         */
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