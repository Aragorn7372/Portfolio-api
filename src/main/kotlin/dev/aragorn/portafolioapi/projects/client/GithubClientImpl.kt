package dev.aragorn.portafolioapi.projects.client

import dev.aragorn.portafolioapi.projects.dto.GithubPagesResponse
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.dto.GithubTreeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.time.Instant
import java.util.logging.Logger

/**
 * Implementación de [GithubClient] con el `RestClient` síncrono de Spring.
 *
 * ## Comportamiento común
 * - **Hilos:** cada llamada HTTP se ejecuta en `Dispatchers.IO` para no bloquear el hilo de la corrutina.
 * - **Reintentos:** los errores 5xx y los timeouts de red se reintentan hasta [MAX_ATTEMPTS]
 *   veces con backoff exponencial (500 ms, 1 s, ...). El resto de errores falla a la primera.
 * - **Cuota:** tras cada respuesta se lee `X-RateLimit-Remaining`. Si queda menos de 100 se avisa
 *   en el log y si llega a 0 se lanza [GithubRateLimitExceededException] para cortar el refresco.
 * - **Errores:** los errores HTTP se traducen a la jerarquía [GithubException] (ver [mapStatusException]).
 * - **Paginación:** los listados piden páginas de [PER_PAGE] elementos hasta que llega una
 *   incompleta, con un tope de [MAX_PAGES] páginas.
 *
 * @param restClient cliente `githubRestClient`, configurado en
 *   [dev.aragorn.portafolioapi.common.config.RestClientConfig.githubRestClient].
 */
@Component
class GithubClientImpl(
    @Qualifier("githubRestClient") private val restClient: RestClient,
) : GithubClient {

    private val log: Logger = Logger.getLogger(GithubClientImpl::class.java.name)

    companion object {
        /** Elementos por página en los listados (máximo que permite GitHub). */
        private const val PER_PAGE = 100
        /** Tope de páginas por listado, para no entrar en un bucle infinito (5000 repositorios). */
        private const val MAX_PAGES = 50

        /** Intentos totales por llamada, contando el primero. */
        private const val MAX_ATTEMPTS = 3

        /** Espera antes del primer reintento. Se duplica en cada reintento. */
        private const val INITIAL_BACKOFF_MS = 500L
        private val REPOS_TYPE = object : ParameterizedTypeReference<List<GithubRepositoryResponse>>() {}
        private val LANGUAGES_TYPE = object : ParameterizedTypeReference<Map<String, Long>>() {}
        private val COMMITS_TYPE = object : ParameterizedTypeReference<List<Any>>() {}
        private val PAGES_TYPE = object : ParameterizedTypeReference<GithubPagesResponse>() {}
        private val TREE_TYPE = object : ParameterizedTypeReference<GithubTreeResponse>() {}

        /** Extrae el número de la última página del enlace `rel="last"` de la cabecera `Link`. */
        private val LAST_PAGE_REGEX = Regex("""<[^>]*[?&]page=(\d+)[^>]*>;\s*rel="last"""")
    }

    override suspend fun findUserRepositories(username: String): List<GithubRepositoryResponse> {
        log.info("Buscando repositorios del usuario $username")
        return fetchAllPages("/users/$username/repos")
    }

    override suspend fun findOrganizationRepositories(organization: String): List<GithubRepositoryResponse> {
        log.info("Buscando repositorios de la organización $organization")
        return fetchAllPages("/orgs/$organization/repos")
    }

    override suspend fun findLanguages(owner: String, repository: String): Map<String, Long> {
        return try {
            val entity = executeWithRetry {
                restClient.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repository)
                    .retrieve()
                    .toEntity(LANGUAGES_TYPE)
            }
            checkRateLimit(entity.headers, "$owner/$repository languages")
            entity.body ?: emptyMap()
        } catch (_:GithubNotFoundException) {
            emptyMap()
        }
    }

    /**
     * Cuenta los commits sin descargarlos todos.
     *
     * Pide una página de un solo commit (`per_page=1`) y lee de la cabecera `Link` el número de
     * la última página, que coincide con el total de commits. Si no hay cabecera `Link` (hay 0 o 1
     * commits) se usa el tamaño del cuerpo. Un `409 Conflict` significa repositorio vacío y
     * devuelve `0`.
     */
    override suspend fun findCommitCount(owner: String, repository: String): Int {
        return try {
            val entity = executeWithRetry {
                try {
                    restClient.get()
                        .uri("/repos/{owner}/{repo}/commits?per_page=1", owner, repository)
                        .retrieve()
                        .toEntity(COMMITS_TYPE)
                } catch (ex: HttpStatusCodeException) {
                    if (ex.statusCode == HttpStatus.CONFLICT) return@executeWithRetry null
                    throw ex
                }
            } ?: return 0
            checkRateLimit(entity.headers, "$owner/$repository commits")
            parseCommitCount(entity.headers.getFirst(HttpHeaders.LINK), entity.body?.size ?: 0)
        } catch (_: GithubNotFoundException) {
            0
        }
    }

    override suspend fun findPagesUrl(owner: String, repository: String): String? {
        return try {
            val entity = executeWithRetry {
                restClient.get()
                    .uri("/repos/{owner}/{repo}/pages", owner, repository)
                    .retrieve()
                    .toEntity(PAGES_TYPE)
            }
            checkRateLimit(entity.headers, "$owner/$repository pages")
            entity.body?.htmlUrl?.takeIf { it.isNotBlank() }
        } catch (_: GithubNotFoundException) {
            null
        }
    }

    /**
     * Descarga el árbol recursivo del repositorio. Si GitHub lo trunca, se avisa en el log y se
     * devuelve la parte recibida. Se descartan los nodos sin ruta.
     */
    override suspend fun findRepositoryTree(owner: String, repository: String, ref: String): List<String> {
        return try {
            val entity = executeWithRetry {
                restClient.get()
                    .uri("/repos/{owner}/{repo}/git/trees/{ref}?recursive=1", owner, repository, ref)
                    .retrieve()
                    .toEntity(TREE_TYPE)
            }
            checkRateLimit(entity.headers, "$owner/$repository tree@$ref")
            val body = entity.body ?: return emptyList()
            if (body.truncated) {
                log.warning("Árbol truncado para $owner/$repository@$ref; se usa detección parcial")
            }
            body.tree.mapNotNull { it.path.takeIf { path -> path.isNotBlank() } }
        } catch (_: GithubNotFoundException) {
            emptyList()
        }
    }

    /**
     * Descarga el fichero en crudo con `Accept: application/vnd.github.raw`, para no tener que
     * decodificar el Base64 que GitHub devuelve por defecto.
     */
    override suspend fun findFileContent(owner: String, repository: String, path: String, ref: String): String? {
        return try {
            val entity = executeWithRetry {
                restClient.get()
                    .uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repository, path, ref)
                    .header("Accept", "application/vnd.github.raw")
                    .retrieve()
                    .toEntity(String::class.java)
            }
            checkRateLimit(entity.headers, "$owner/$repository contents:$path@$ref")
            entity.body?.takeIf { it.isNotBlank() }
        } catch (_: GithubNotFoundException) {
            null
        }
    }

    /**
     * Recorre todas las páginas de un listado de repositorios.
     *
     * @param basePath ruta del listado, por ejemplo `/users/{user}/repos`.
     * @return la concatenación de todas las páginas.
     * @throws GithubInvalidResponseException si una página llega sin cuerpo.
     */
    private suspend fun fetchAllPages(basePath: String): List<GithubRepositoryResponse> {
        val all = mutableListOf<GithubRepositoryResponse>()
        var page = 1
        while (page <= MAX_PAGES) {
            val entity = executeWithRetry {
                restClient.get()
                    .uri { builder ->
                        builder.path(basePath)
                            .queryParam("per_page", PER_PAGE)
                            .queryParam("page", page)
                            .build()
                    }
                    .retrieve()
                    .toEntity(REPOS_TYPE)
            }
            checkRateLimit(entity.headers, "$basePath page=$page")
            val batch = entity.body
                ?: throw GithubInvalidResponseException("GitHub devolvió una respuesta vacía en $basePath page=$page")
            all.addAll(batch)
            if (batch.size < PER_PAGE) break
            page++
        }
        return all
    }

    /**
     * Obtiene el total de commits a partir de la cabecera `Link` de una consulta con `per_page=1`.
     *
     * @param linkHeader valor de la cabecera `Link`, o `null` si no venía.
     * @param bodySize número de commits del cuerpo. Se usa si no se puede leer la última página.
     * @return número total de commits.
     */
    private fun parseCommitCount(linkHeader: String?, bodySize: Int): Int {
        if (linkHeader == null) return bodySize
        return LAST_PAGE_REGEX.find(linkHeader)?.groupValues?.get(1)?.toIntOrNull() ?: bodySize
    }


    /**
     * Revisa la cuota restante después de una respuesta correcta.
     *
     * @param headers cabeceras de la respuesta.
     * @param context descripción de la llamada, para los logs.
     * @throws GithubRateLimitExceededException si la cuota restante es 0.
     */
    private fun checkRateLimit(headers: HttpHeaders, context: String) {
        val remaining = headers.getFirst("X-RateLimit-Remaining")?.toLongOrNull()
        if (remaining != null && remaining <= 0) {
            throw GithubRateLimitExceededException(
                resetAt = parseReset(headers),
                message = "Cuota de GitHub agotada ($context). Reset: ${parseReset(headers)}",
            )
        }
        if (remaining != null && remaining < 100) {
            log.warning("Cuota de GitHub baja: quedan $remaining ($context)")
        }
    }

    /**
     * Calcula cuándo se renueva la cuota: primero con `X-RateLimit-Reset` (epoch en segundos) y,
     * si no está, con `Retry-After` (segundos desde ahora).
     *
     * @return el instante de renovación, o `null` si ninguna cabecera lo indica.
     */
    private fun parseReset(headers: HttpHeaders): Instant? {
        headers.getFirst("X-RateLimit-Reset")?.toLongOrNull()?.let { return Instant.ofEpochSecond(it) }
        headers.getFirst("Retry-After")?.toLongOrNull()?.let { return Instant.now().plusSeconds(it) }
        return null
    }

    /**
     * Ejecuta una llamada bloqueante en `Dispatchers.IO` y aplica la política de reintentos.
     *
     * - [GithubException]: se propaga sin reintentar (ya está clasificada).
     * - HTTP 5xx: se reintenta con backoff. Si se agotan los intentos se traduce con [mapStatusException].
     * - Otros códigos HTTP: se traducen con [mapStatusException] sin reintentar.
     * - Error de red o timeout: se reintenta. Si se agotan los intentos se lanza [GithubTimeoutException].
     *
     * @param action llamada HTTP a ejecutar.
     * @return el resultado de la llamada.
     */
    private suspend fun <T> executeWithRetry(action: () -> T): T {
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                return withContext(Dispatchers.IO) { action() }
            } catch (ex: GithubException) {
                throw ex
            } catch (ex: HttpStatusCodeException) {
                if (ex.statusCode.is5xxServerError && attempt + 1 < MAX_ATTEMPTS) {
                    attempt++
                    log.warning("GitHub ${ex.statusCode} (intento $attempt/$MAX_ATTEMPTS), reintentando en ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs *= 2
                } else {
                    throw mapStatusException(ex)
                }
            } catch (ex: ResourceAccessException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    throw GithubTimeoutException("Timeout comunicando con GitHub: ${ex.message}", ex)
                }
                log.warning("Timeout con GitHub (intento $attempt/$MAX_ATTEMPTS), reintentando en ${backoffMs}ms")
                delay(backoffMs)
                backoffMs *= 2
            }
        }
    }

    /**
     * Traduce un error HTTP de GitHub a la excepción de dominio que le corresponde.
     *
     * | Condición                                                             | Excepción                              |
     * |-----------------------------------------------------------------------|----------------------------------------|
     * | 403 con "rate limit" en el cuerpo, 429 o `X-RateLimit-Remaining: 0`   | [GithubRateLimitExceededException]     |
     * | 404                                                                   | [GithubNotFoundException]              |
     * | 401                                                                   | [GithubUnauthorizedException]          |
     * | 5xx o cualquier otro código                                           | [GithubServerException]                |
     *
     * @param ex error HTTP original.
     * @return la excepción de dominio que corresponde.
     */
    private fun mapStatusException(ex: HttpStatusCodeException): GithubException {
        val headers = ex.responseHeaders ?: HttpHeaders()
        val body = ex.responseBodyAsString
        val rateLimited = ex.statusCode == HttpStatus.FORBIDDEN && body.contains("rate limit", ignoreCase = true) ||
            ex.statusCode == HttpStatus.TOO_MANY_REQUESTS ||
            headers.getFirst("X-RateLimit-Remaining") == "0"
        if (rateLimited) {
            return GithubRateLimitExceededException(
                resetAt = parseReset(headers),
                message = "Rate limit de GitHub excedido (${ex.statusCode}). Reset: ${parseReset(headers)}",
            )
        }
        return when {
            ex.statusCode == HttpStatus.NOT_FOUND -> GithubNotFoundException("Recurso no encontrado en GitHub: $body")
            ex.statusCode == HttpStatus.UNAUTHORIZED -> GithubUnauthorizedException(
                "Token de GitHub inválido o ausente. Revisa APP_GITHUB_TOKEN.",
            )
            ex.statusCode.is5xxServerError -> GithubServerException("GitHub devolvió ${ex.statusCode}", ex)
            else -> GithubServerException("Error inesperado de GitHub: ${ex.statusCode} $body", ex)
        }
    }
}
