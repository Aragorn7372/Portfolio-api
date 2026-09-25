package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Respuesta del endpoint `GET /repos/{owner}/{repo}/pages` de GitHub.
 *
 * @property htmlUrl URL pública del sitio (`html_url`), o `null` si no viene informada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPagesResponse(
    @JsonProperty("html_url")
    val htmlUrl: String?,
)
