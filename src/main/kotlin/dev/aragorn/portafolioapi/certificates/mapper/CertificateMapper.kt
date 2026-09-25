package dev.aragorn.portafolioapi.certificates.mapper

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
import dev.aragorn.portafolioapi.certificates.model.Certificates
import org.springframework.stereotype.Component
import java.time.LocalDate

/** Conversiones entre la entidad [Certificates] y [CertificatesResponseDto]. */
@Component
class CertificateMapper {
    /**
     * Convierte la entidad en DTO, con la fecha en formato ISO `yyyy-MM-dd`.
     *
     * @param model entidad persistida.
     * @return el DTO de respuesta.
     */
    fun certificateToDto(model: Certificates): CertificatesResponseDto {
        return CertificatesResponseDto(
            model.name,
            model.url,
            model.date.toString(),
        )
    }

    /**
     * Convierte el DTO de origen en entidad. El id se saca de la URL.
     *
     * @param dto certificado recibido del servicio externo.
     * @return la entidad lista para persistir.
     * @throws InvalidCertificateException si la fecha no es una fecha ISO válida.
     * @throws IllegalArgumentException si la URL no contiene un identificador de documento.
     */
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
