package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.exceptions.CertificationEmptyException
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.logging.Logger

@Component
class CertificatesClientImpl(
    @Qualifier("certificatesRestClient") private val restClient: RestClient,
    @Value("\${app.certificates.base-url}")
    private val baseUrl: String,
) : CertificateClient {

    private val log: Logger = Logger.getLogger(CertificatesClientImpl::class.java.name)

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 1000L
        private val CERTS_TYPE = object : ParameterizedTypeReference<List<CertificatesResponseDto>>() {}
    }

    override suspend fun findCertificates(): List<CertificatesResponseDto> {
        require(baseUrl.isNotBlank()) { "APP_CERTIFICATES_BASE_URL no está configurado" }
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                log.info("searching certificates (intento ${attempt + 1}/$MAX_ATTEMPTS)")
                val entity = withContext(Dispatchers.IO) {
                    restClient.get()
                        .uri(baseUrl)
                        .retrieve()
                        .toEntity(CERTS_TYPE)
                }
                val certificates = entity.body
                if (certificates == null) {
                    // Body vacío (p. ej. Apps Script en frío): se reintenta con diagnóstico.
                    attempt++
                    log.warning(
                        "Respuesta sin cuerpo de certificados: " +
                            "status=${entity.statusCode}, " +
                            "contentType=${entity.headers.contentType}, " +
                            "contentLength=${entity.headers.contentLength} " +
                            "(intento $attempt/$MAX_ATTEMPTS)",
                    )
                    if (attempt >= MAX_ATTEMPTS) {
                        throw CertificationEmptyException(
                            "la API devolvió una respuesta vacia " +
                                "(status=${entity.statusCode}, contentType=${entity.headers.contentType})",
                        )
                    }
                    delay(backoffMs)
                    backoffMs *= 2
                    continue
                }
                if (certificates.isEmpty()) {
                    throw CertificationEmptyException("No puede llegar la lista vacia")
                }
                return certificates
            } catch (ex: CertificationEmptyException) {
                throw ex
            } catch (ex: HttpStatusCodeException) {
                if (ex.statusCode.is5xxServerError && attempt + 1 < MAX_ATTEMPTS) {
                    attempt++
                    log.warning("Certificados ${ex.statusCode} (intento $attempt/$MAX_ATTEMPTS), reintentando en ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs *= 2
                } else {
                    val status = ex.statusCode
                    if (status == HttpStatus.NOT_FOUND) {
                        throw CertificationEmptyException("endpoint de certificados no encontrado (404): $baseUrl")
                    }
                    log.severe("Error obteniendo certificados: $status ${ex.responseBodyAsString.take(300)}")
                    throw ex
                }
            } catch (ex: ResourceAccessException) {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    log.severe("Error obteniendo certificados: ${ex.message}")
                    throw ex
                }
                log.warning("Timeout con certificados (intento $attempt/$MAX_ATTEMPTS), reintentando en ${backoffMs}ms")
                delay(backoffMs)
                backoffMs *= 2
            } catch (ex: Exception) {
                log.severe("Error obteniendo certificados: ${ex.message}")
                throw ex
            }
        }
    }
}
