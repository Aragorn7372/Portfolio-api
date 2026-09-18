package dev.aragorn.portafolioapi.projects.validator

import dev.aragorn.portafolioapi.common.service.validator.Validator
import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse
import dev.aragorn.portafolioapi.projects.exceptions.InvalidGithubRepositoryException
import jakarta.validation.Validator as JakartaValidator
import org.springframework.stereotype.Component

@Component
class GithubRepositoryValidator(
    private val validator: JakartaValidator,
) : Validator<GithubRepositoryResponse> {
    override fun validate(value: GithubRepositoryResponse) {
        val violations = validator.validate(value)
        if (violations.isNotEmpty()) {
            throw InvalidGithubRepositoryException(violations.joinToString { it.message })
        }
    }
}
