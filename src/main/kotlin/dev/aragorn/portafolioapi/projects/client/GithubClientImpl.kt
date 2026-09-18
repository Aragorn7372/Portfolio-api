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

@Component
class GithubClientImpl(
    @Qualifier("githubRestClient") private val restClient: RestClient,
) : GithubClient {

    private val log: Logger = Logger.getLogger(GithubClientImpl::class.java.name)

    companion object {
        private const val PER_PAGE = 100
        private const val MAX_PAGES = 50
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 500L
        private val REPOS_TYPE = object : ParameterizedTypeReference<List<GithubRepositoryResponse>>() {}
        private val LANGUAGES_TYPE = object : ParameterizedTypeReference<Map<String, Long>>() {}
        private val COMMITS_TYPE = object : ParameterizedTypeReference<List<Any>>() {}
        private val PAGES_TYPE = object : ParameterizedTypeReference<GithubPagesResponse>() {}
        private val TREE_TYPE = object : ParameterizedTypeReference<GithubTreeResponse>() {}
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
        } catch (ex: GithubNotFoundException) {
            emptyMap()
        }
    }

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
        } catch (ex: GithubNotFoundException) {
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
        } catch (ex: GithubNotFoundException) {
            null
        }
    }

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
        } catch (ex: GithubNotFoundException) {
            emptyList()
        }
    }

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
        } catch (ex: GithubNotFoundException) {
            null
        }
    }

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

    private fun parseCommitCount(linkHeader: String?, bodySize: Int): Int {
        if (linkHeader == null) return bodySize
        return LAST_PAGE_REGEX.find(linkHeader)?.groupValues?.get(1)?.toIntOrNull() ?: bodySize
    }


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

    private fun parseReset(headers: HttpHeaders): Instant? {
        headers.getFirst("X-RateLimit-Reset")?.toLongOrNull()?.let { return Instant.ofEpochSecond(it) }
        headers.getFirst("Retry-After")?.toLongOrNull()?.let { return Instant.now().plusSeconds(it) }
        return null
    }

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
