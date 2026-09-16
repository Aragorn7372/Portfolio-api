package dev.aragorn.portafolioapi.certificates.client

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto

interface CertidicateClient {
    suspend fun findCertificates():List<CertificatesResponseDto>
}