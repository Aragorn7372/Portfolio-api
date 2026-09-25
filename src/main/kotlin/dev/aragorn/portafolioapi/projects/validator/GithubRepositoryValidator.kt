package dev.aragorn.portafolioapi.projects.validator

import dev.aragorn.portafolioapi.common.service.validator.Validator
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.exceptions.InvalidGithubRepositoryException
import jakarta.validation.Validator as JakartaValidator
import org.springframework.stereotype.Component

/**
 * Valida con Bean Validation cada [GithubRepositoryResponse] que llega de GitHub.
 *
 * Aplica las anotaciones del DTO (id positivo, URL de `github.com`, contadores no negativos,
 * propietario válido...). Durante el refresco, los repositorios que no pasan se descartan con un
 * aviso en el log. No hacen fallar el refresco completo.
 *
 * @param validator validador de Jakarta que proporciona Spring.
 */
@Component
class GithubRepositoryValidator(
    private val validator: JakartaValidator,
) : Validator<GithubRepositoryResponse> {
    /**
     * @throws InvalidGithubRepositoryException con todas las violaciones separadas por comas.
     */
    override fun validate(value: GithubRepositoryResponse) {
        val violations = validator.validate(value)
        if (violations.isNotEmpty()) {
            throw InvalidGithubRepositoryException(violations.joinToString { it.message })
        }
    }
}
