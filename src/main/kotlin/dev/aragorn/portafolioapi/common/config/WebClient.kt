package dev.aragorn.portafolioapi.common.config

import dev.aragorn.portafolioapi.projects.config.GithubProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Define los [RestClient] con los que la API llama a servicios externos.
 *
 * Hay un cliente por destino, cada uno con sus timeouts y cabeceras. Se inyectan por nombre con
 * `@Qualifier("certificatesRestClient")` o `@Qualifier("githubRestClient")`.
 */
@Configuration
class RestClientConfig {

    /**
     * Cliente genérico sin configuración específica (sin URL base, timeouts ni cabeceras).
     *
     * @return un [RestClient] por defecto.
     */
    @Bean
    fun restClient(): RestClient {
        return RestClient.builder().build()
    }

    /**
     * Cliente para el servicio externo de certificados.
     *
     * - Timeout de conexión de 5 s y de lectura de 15 s. La lectura es generosa porque el
     *   servicio puede tardar en responder la primera vez tras un rato inactivo.
     * - Cabeceras por defecto: `Accept: application/json` y `User-Agent: Portafolio-Api`.
     * - No tiene URL base: la URL completa la pone
     *   [dev.aragorn.portafolioapi.certificates.client.CertificatesClientImpl] a partir de
     *   `app.certificates.base-url`.
     *
     * @return el cliente de certificados.
     */
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

    /**
     * Cliente para la API REST de GitHub.
     *
     * - URL base y timeouts salen de [GithubProperties] (`app.github.*`).
     * - Cabeceras por defecto: `Accept: application/vnd.github+json`, la versión de API
     *   `X-GitHub-Api-Version: 2022-11-28` y `User-Agent: Portafolio-Api`.
     * - Si hay token configurado se envía como `Authorization: Bearer <token>`. Sin token la
     *   API funciona igual, pero con una cuota de peticiones mucho menor.
     *
     * @param properties configuración de GitHub.
     * @return el cliente de GitHub.
     */
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
