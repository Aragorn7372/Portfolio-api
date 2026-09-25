package dev.aragorn.portafolioapi.common.config

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.logging.Level
import java.util.logging.Logger


/**
 * Traduce a respuestas JSON uniformes las excepciones que salen de los controladores.
 *
 * Todas las respuestas de error tienen la forma `{"error": "<código>"}` y nunca incluyen
 * trazas ni mensajes internos, para no filtrar detalles de la implementación al cliente.
 * El detalle completo se deja solo en el log.
 *
 * | Excepción                                                                 | HTTP | `error`             |
 * |---------------------------------------------------------------------------|------|---------------------|
 * | [MethodArgumentNotValidException]                                         | 400  | `validation_failed` |
 * | [HttpMessageNotReadableException], [ConstraintViolationException]         | 400  | `bad_request`       |
 * | Excepción de Spring con código propio ([ErrorResponse])                   | suyo | ver [handleSpringError] |
 * | Cualquier otra [Exception]                                                | 500  | `internal_error`    |
 *
 * Así una ruta inexistente da `404 not_found`, un verbo no permitido `405 method_not_allowed`
 * y un `Content-Type` no soportado `415 unsupported_media_type`, en lugar de un 500.
 *
 * Los errores que se generan en los filtros de servlet (origen no autorizado, token de
 * visita ausente, rate limit) no pasan por aquí: esos filtros escriben la respuesta ellos mismos.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private val log: Logger = Logger.getLogger(GlobalExceptionHandler::class.java.name)

    /**
     * Maneja los fallos de validación de Bean Validation sobre un `@RequestBody` anotado con `@Valid`.
     *
     * @param ex excepción con los errores de cada campo.
     * @return `400` con `{"error":"validation_failed","details":["campo: mensaje", ...]}`.
     *   Solo se devuelven el nombre del campo y el mensaje de la restricción, nunca el valor recibido.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val details = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "validation_failed", "details" to details))
    }

    /**
     * Maneja las peticiones mal formadas: JSON que no se puede leer, tipos incorrectos o
     * restricciones violadas fuera del cuerpo (parámetros, cabeceras).
     *
     * @param ex excepción original, que solo se registra en el log como aviso.
     * @return `400` con `{"error":"bad_request"}`.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class, ConstraintViolationException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<Map<String, Any>> {
        log.warning("Petición inválida: ${ex.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "bad_request"))
    }

    /**
     * Última red de seguridad para cualquier excepción que no tenga un manejador específico.
     *
     * Primero comprueba si es una excepción de Spring que ya trae su código HTTP ([ErrorResponse]):
     * ruta inexistente, verbo no permitido, `Content-Type` no soportado, `ResponseStatusException`...
     * En ese caso responde con ese código (ver [handleSpringError]) en lugar de un 500, porque
     * es un error del cliente y no de la API.
     *
     * Para el resto, registra la excepción con su traza y recorre hasta 5 niveles de `cause`,
     * con las 5 primeras líneas de la pila de cada uno. Así se puede diagnosticar el origen real
     * del error (por ejemplo, un fallo de serialización envuelto por Spring) sin inundar el log.
     *
     * @param ex excepción no controlada.
     * @return el código de la excepción de Spring o, si no lo tiene, `500` con `{"error":"internal_error"}`.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, Any>> {
        if (ex is ErrorResponse) return handleSpringError(ex)
        log.log(Level.SEVERE, "Error no controlado: ${ex.message}", ex)
        var cause = ex.cause
        var depth = 0
        while (cause != null && depth < 5) {
            log.severe("Caused by [$depth] ${cause.javaClass.name}: ${cause.message}")
            cause.stackTrace.take(5).forEach { log.severe("    at $it") }
            cause = cause.cause
            depth++
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "internal_error"))
    }

    /**
     * Responde con el código HTTP que ya trae una excepción de Spring.
     *
     * | HTTP        | `error`                  |
     * |-------------|--------------------------|
     * | 404         | `not_found`              |
     * | 405         | `method_not_allowed`     |
     * | 406         | `not_acceptable`         |
     * | 415         | `unsupported_media_type` |
     * | Otro 4xx    | `bad_request`            |
     * | 5xx         | `internal_error`         |
     *
     * Solo deja una línea de aviso en el log, sin traza, porque no es un fallo de la API.
     *
     * @param ex excepción de Spring con su código HTTP.
     * @return el código de la excepción con `{"error": "<código>"}`.
     */
    private fun handleSpringError(ex: ErrorResponse): ResponseEntity<Map<String, Any>> {
        val status = ex.statusCode
        log.warning("${status.value()} ${ex.javaClass.simpleName}: ${(ex as? Exception)?.message}")
        val error = when (status.value()) {
            404 -> "not_found"
            405 -> "method_not_allowed"
            406 -> "not_acceptable"
            415 -> "unsupported_media_type"
            else -> if (status.is4xxClientError) "bad_request" else "internal_error"
        }
        return ResponseEntity.status(status).body(mapOf("error" to error))
    }
}
