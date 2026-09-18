package dev.aragorn.portafolioapi.common.config

import dev.aragorn.portafolioapi.projects.config.GithubProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {

    @Bean
    fun restClient(): RestClient {
        return RestClient.builder().build()
    }

    @Bean
    fun certificatesRestClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(15))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("User-Agent", "Portafolio-Api")
            .build()
    }

    @Bean
    fun githubRestClient(properties: GithubProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(properties.timeoutConnectMs))
            setReadTimeout(Duration.ofMillis(properties.timeoutReadMs))
        }
        val builder = RestClient.builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader("User-Agent", "Portafolio-Api")
        properties.token?.takeIf { it.isNotBlank() }?.let { token ->
            builder.defaultHeader("Authorization", "Bearer $token")
        }
        return builder.build()
    }
}
