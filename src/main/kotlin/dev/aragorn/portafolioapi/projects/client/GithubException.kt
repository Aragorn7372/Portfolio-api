package dev.aragorn.portafolioapi.projects.client

import java.time.Instant

/**
 * Raíz de los errores que lanza [GithubClient] al hablar con la API de GitHub.
 *
 * Es `sealed`, así que un `when` sobre ella cubre todos los casos. Durante el refresco,
 * [dev.aragorn.portafolioapi.projects.service.GithubServiceImpl] trata
 * [GithubRateLimitExceededException] como fatal (corta el refresco) y el resto como un fallo
 * parcial del repositorio afectado.
 *
 * @param message descripción del error.
 * @param cause excepción original, si la hay.
 */
sealed class GithubException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


/**
 * Se ha agotado la cuota de peticiones de la API.
 *
 * @property resetAt instante en que se renueva la cuota, si GitHub lo indica.
 */
class GithubRateLimitExceededException(
    val resetAt: Instant?,
    message: String,
) : GithubException(message)

/** El token configurado no es válido, ha caducado o falta (HTTP 401). */
class GithubUnauthorizedException(message: String) : GithubException(message)

/** El recurso pedido no existe o no es visible con las credenciales actuales (HTTP 404). */
class GithubNotFoundException(message: String) : GithubException(message)

/** GitHub ha respondido con un 5xx tras agotar los reintentos, o con un código no esperado. */
class GithubServerException(message: String, cause: Throwable? = null) : GithubException(message, cause)

/** No se ha podido conectar o leer la respuesta a tiempo tras agotar los reintentos. */
class GithubTimeoutException(message: String, cause: Throwable? = null) : GithubException(message, cause)

/** La respuesta de GitHub no tiene la forma esperada, por ejemplo una página de listado sin cuerpo. */
class GithubInvalidResponseException(message: String) : GithubException(message)
