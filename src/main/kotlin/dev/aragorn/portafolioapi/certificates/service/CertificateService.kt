package dev.aragorn.portafolioapi.certificates.service

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.model.Certificates


interface CertificateService {
    suspend fun refresh()
    suspend fun getAll(): List<CertificatesResponseDto>
}