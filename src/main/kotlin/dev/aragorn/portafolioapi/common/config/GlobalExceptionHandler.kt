package dev.aragorn.portafolioapi.common.config

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
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
 * | Cualquier otra [Exception]                                                | 500  | `internal_error`    |
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
     * Registra la excepción con su traza y recorre hasta 5 niveles de `cause`, con las 5
     * primeras líneas de la pila de cada uno. Así se puede diagnosticar el origen real del
     * error (por ejemplo, un fallo de serialización envuelto por Spring) sin inundar el log.
     *
     * @param ex excepción no controlada.
     * @return `500` con `{"error":"internal_error"}`.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, Any>> {
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
}
