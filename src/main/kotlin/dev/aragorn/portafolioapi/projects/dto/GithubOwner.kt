package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubOwner(
    @NotBlank
    val login: String,
    @NotBlank
    @JsonProperty("avatar_url")
    val avatarUrl: String,
)
