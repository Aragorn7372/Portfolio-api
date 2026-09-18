package dev.aragorn.portafolioapi.projects.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.github")
data class GithubProperties(
    val personal: String = "",
    val organizations: List<String> = emptyList(),
    val excludedRepositories: Set<String> = emptySet(),
    val token: String? = null,
    val baseUrl: String = "https://api.github.com",
    val timeoutConnectMs: Long = 3000,
    val timeoutReadMs: Long = 5000,
)
