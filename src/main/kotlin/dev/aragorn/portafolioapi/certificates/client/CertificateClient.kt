package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto

/**
 * Acceso al servicio externo que publica la lista de certificados.
 *
 * @see CertificatesClientImpl
 */
interface CertificateClient {
    /**
     * Descarga la lista completa de certificados.
     *
     * @return los certificados publicados. Nunca devuelve una lista vacía.
     * @throws dev.aragorn.portafolioapi.certificates.exceptions.CertificationEmptyException si no llegan datos que se puedan usar.
     */
    suspend fun findCertificates():List<CertificatesResponseDto>
}