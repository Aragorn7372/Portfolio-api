package dev.aragorn.portafolioapi.certificates.validator

import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.common.service.validator.Validator
import jakarta.validation.Validator as JakartaValidator
import org.springframework.stereotype.Component

/**
 * Valida con Bean Validation cada [CertificatesResponseDto] que llega del servicio externo.
 *
 * A diferencia de los proyectos, aquí un solo certificado inválido hace fallar todo el refresco,
 * y se mantienen los datos que ya había.
 *
 * @param validator validador de Jakarta que proporciona Spring.
 */
@Component
class CertificateValidator(
    private val validator: JakartaValidator
): Validator<CertificatesResponseDto> {
    /**
     * @throws InvalidCertificateException con todas las violaciones separadas por comas.
     */
    override fun validate(value: CertificatesResponseDto) {
        val violation = validator.validate(value)
        if (violation.isNotEmpty()) {
            throw InvalidCertificateException(violation.joinToString { it.message })
        }
    }

}