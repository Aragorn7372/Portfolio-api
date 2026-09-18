package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

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
