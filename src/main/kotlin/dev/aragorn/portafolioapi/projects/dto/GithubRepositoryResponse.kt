package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

/**
 * Repositorio tal y como lo devuelven los listados de GitHub (`/users/{user}/repos` y
 * `/orgs/{org}/repos`).
 *
 * Lleva anotaciones de Bean Validation que comprueba
 * [dev.aragorn.portafolioapi.projects.validator.GithubRepositoryValidator] antes de
 * persistirlo. Los repositorios que no las cumplen se descartan del refresco.
 *
 * @property id id numérico del repositorio. Debe ser positivo.
 * @property name nombre corto. Obligatorio.
 * @property fullName nombre completo `propietario/repositorio` (`full_name`). Obligatorio.
 * @property htmlUrl URL pública (`html_url`). Tiene que ser una URL de `github.com`.
 * @property description descripción, si tiene.
 * @property fork `true` si es un fork.
 * @property owner propietario del repositorio. Se valida en cascada.
 * @property language lenguaje principal según GitHub. No se usa para el reparto por lenguajes.
 * @property stargazersCount número de estrellas (`stargazers_count`). No puede ser negativo.
 * @property forksCount número de forks (`forks_count`). No puede ser negativo.
 * @property topics temas del repositorio. Nunca es nulo.
 * @property createdAt fecha de creación en ISO-8601 (`created_at`).
 * @property updatedAt fecha de la última actualización en ISO-8601 (`updated_at`).
 * @property pushedAt fecha del último push en ISO-8601 (`pushed_at`).
 * @property defaultBranch rama por defecto (`default_branch`). Si falta, se usa `HEAD`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRepositoryResponse(
    @Positive
    val id: Long,
    @NotBlank
    val name: String,
    @NotBlank
    @JsonProperty("full_name")
    val fullName: String,
    @NotBlank
    @Pattern(regexp = "https://github\\.com/.*")
    @JsonProperty("html_url")
    val htmlUrl: String,
    val description: String?,
    val fork: Boolean = false,
    @Valid
    @NotNull
    val owner: GithubOwner,
    val language: String?,
    @PositiveOrZero
    @JsonProperty("stargazers_count")
    val stargazersCount: Int = 0,
    @PositiveOrZero
    @JsonProperty("forks_count")
    val forksCount: Int = 0,
    @NotNull
    val topics: List<String> = emptyList(),
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
    @JsonProperty("pushed_at")
    val pushedAt: String?,
    @JsonProperty("default_branch")
    val defaultBranch: String? = null,
)
