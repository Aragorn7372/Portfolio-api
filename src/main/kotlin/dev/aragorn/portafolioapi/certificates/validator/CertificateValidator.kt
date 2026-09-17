package dev.aragorn.portafolioapi.certificates.validator

import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.common.service.validator.Validator
import jakarta.validation.Validator as JakartaValidator
import org.springframework.stereotype.Component

@Component
class CertificateValidator(
    private val validator: JakartaValidator
): Validator<CertificatesResponseDto> {
    override fun validate(value: CertificatesResponseDto) {
        val violation = validator.validate(value)
        if (violation.isNotEmpty()) {
            throw InvalidCertificateException(violation.joinToString { it.message })
        }
    }

}