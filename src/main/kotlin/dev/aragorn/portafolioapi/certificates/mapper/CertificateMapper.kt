package dev.aragorn.portafolioapi.certificates.mapper

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
import dev.aragorn.portafolioapi.certificates.model.Certificates
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class CertificateMapper {
    fun certificateToDto(model: Certificates): CertificatesResponseDto {
        return CertificatesResponseDto(
            model.name,
            model.url,
            model.date.toString(),
        )
    }

    fun dtoToModel(dto: CertificatesResponseDto): Certificates {
        return Certificates(
            dto.titulo,
            parseDate(dto),
            dto.url,
        )
    }

    private fun parseDate(dto: CertificatesResponseDto): LocalDate =
        runCatching { LocalDate.parse(dto.fecha) }
            .getOrElse { throw InvalidCertificateException("fecha inválida '${dto.fecha}' en '${dto.titulo}'") }
}
