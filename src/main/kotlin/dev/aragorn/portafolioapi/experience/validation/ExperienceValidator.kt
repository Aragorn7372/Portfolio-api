package dev.aragorn.portafolioapi.experience.validation

import dev.aragorn.portafolioapi.common.service.validator.Validator
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.exceptions.ExperienceValidationException
import org.springframework.stereotype.Component
import java.util.logging.Logger
import jakarta.validation.Validator as JakartaValidator


@Component
class ExperienceValidator(
    private val validator: JakartaValidator
): Validator<ExperienceStorageDto> {
    private val log: Logger = Logger.getLogger(ExperienceValidator::class.java.name)

    /**
     * @throws ExperienceValidationException con todas las violaciones separadas por comas.
     */
    override fun validate(value: ExperienceStorageDto) {
        val violation = validator.validate(value)
        if (violation.isNotEmpty()) {
            val message = violation.joinToString { it.message }
            log.warning("experience ${value.id} validation failed: $message")
            throw ExperienceValidationException(message)
        }
    }

    fun validates(values: List<ExperienceStorageDto>) {
        val violations = values.flatMap {
            validator.validate(it)
        }
        if (violations.isNotEmpty()) {
            val message = violations.joinToString { it.message }
            log.warning("experience validation failed: $message")
            throw ExperienceValidationException(message)
        }
        log.info("validated ${values.size} experiences")
    }
}