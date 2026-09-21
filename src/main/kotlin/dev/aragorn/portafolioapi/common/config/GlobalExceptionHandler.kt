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


@ControllerAdvice
class GlobalExceptionHandler {

    private val log: Logger = Logger.getLogger(GlobalExceptionHandler::class.java.name)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val details = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "validation_failed", "details" to details))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, ConstraintViolationException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<Map<String, Any>> {
        log.warning("Petición inválida: ${ex.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "bad_request"))
    }

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
