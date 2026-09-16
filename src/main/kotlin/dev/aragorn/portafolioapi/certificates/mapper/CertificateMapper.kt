package dev.aragorn.portafolioapi.certificates.mapper

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.model.Certificates
import org.springframework.stereotype.Component

@Component
class CertificateMapper {
    fun certificateToDto(model: Certificates): CertificatesResponseDto{
        return CertificatesResponseDto(
            model.name,
            model.url,
            model.date)
    }
    fun dtoToModel(dto: CertificatesResponseDto): Certificates{
        return Certificates(
            dto.titulo,
            dto.fecha,
            dto.url
        )
    }
}