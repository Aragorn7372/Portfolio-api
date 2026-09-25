package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/**
 * Propietario de un repositorio tal y como lo devuelve la API de GitHub (campo `owner`).
 *
 * Solo se mapean los campos que usa la aplicación. El resto se ignora.
 *
 * @property login login del usuario u organización. Obligatorio.
 * @property avatarUrl URL del avatar (`avatar_url`). Obligatoria.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubOwner(
    @NotBlank
    val login: String,
    @NotBlank
    @JsonProperty("avatar_url")
    val avatarUrl: String,
)
