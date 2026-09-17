package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.exceptions.CertificationEmptyException
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

import org.springframework.web.client.body
import java.util.logging.Logger

@Component
class CertificatesClientImpl(
    private val restClient: RestClient
): CertificateClient {
    private val log: Logger =Logger.getLogger(CertificatesClientImpl::class.java.name)
    override suspend fun findCertificates(): List<CertificatesResponseDto> {
        val certificates =  try {
            log.info("searching certificates")
            restClient.get()
                .uri("")
                .retrieve()
                .body<List<CertificatesResponseDto>>()
                ?: throw CertificationEmptyException(
                    "la API devolvió una respuesta vacia"
                )
        }catch (ex:Exception){
            log.severe("Error obteniendo certificados: ${ex.message}")
            throw ex
        }
        if (certificates.isEmpty()) {
            throw CertificationEmptyException(
                "No puede llegar la lista vacia"
            )
        }
        return certificates
    }
}