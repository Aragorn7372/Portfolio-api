package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto

interface CertificateClient {
    suspend fun findCertificates():List<CertificatesResponseDto>
}