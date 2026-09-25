package dev.aragorn.portafolioapi.projects.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del módulo de proyectos (prefijo `app.github`).
 *
 * @property personal usuario de GitHub cuyos repositorios forman el portafolio. Es obligatorio:
 *   si está vacío, el refresco falla.
 * @property organizations organizaciones de las que también se incluyen los repositorios.
 * @property excludedRepositories repositorios que se excluyen, en formato `propietario/nombre`
 *   (no distingue mayúsculas).
 * @property token token de acceso a la API. Es opcional, pero sin él la cuota de peticiones es muy baja.
 * @property baseUrl URL base de la API de GitHub.
 * @property timeoutConnectMs timeout de conexión en milisegundos.
 * @property timeoutReadMs timeout de lectura en milisegundos.
 */
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
