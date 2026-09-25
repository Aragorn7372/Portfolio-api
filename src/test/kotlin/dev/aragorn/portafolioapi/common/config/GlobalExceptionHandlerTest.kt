package dev.aragorn.portafolioapi.common.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    private data class Form(val name: String = "")

    @Test
    @DisplayName("validación devuelve 400 con detalles")
    fun handleValidation() {
        val ex: MethodArgumentNotValidException = mock()
        val bindingResult = BeanPropertyBindingResult(Form(), "target")
        bindingResult.rejectValue("name", "code", "must not be blank")
        whenever(ex.bindingResult).thenReturn(bindingResult)

        val result = handler.handleValidation(ex)

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals(
            mapOf("error" to "validation_failed", "details" to listOf("name: must not be blank")),
            result.body
        )
    }

    @Test
    @DisplayName("petición inválida devuelve 400")
    fun handleBadRequest() {
        val result = handler.handleBadRequest(IllegalArgumentException("boom"))

        assertEquals(HttpStatus.BAD_REQUEST, result.statusCode)
        assertEquals(mapOf("error" to "bad_request"), result.body)
    }

    @Test
    @DisplayName("error no controlado devuelve 500")
    fun handleGeneric() {
        val result = handler.handleGeneric(RuntimeException("boom", IllegalStateException("causa")))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertEquals(mapOf("error" to "internal_error"), result.body)
    }

    @Test
    @DisplayName("ruta inexistente devuelve 404")
    fun handleNotFound() {
        val result = handler.handleGeneric(NoResourceFoundException(HttpMethod.GET, "/loquesea", "/loquesea"))

        assertEquals(HttpStatus.NOT_FOUND, result.statusCode)
        assertEquals(mapOf("error" to "not_found"), result.body)
    }

    @Test
    @DisplayName("verbo no permitido devuelve 405")
    fun handleMethodNotAllowed() {
        val result = handler.handleGeneric(HttpRequestMethodNotSupportedException("DELETE"))

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, result.statusCode)
        assertEquals(mapOf("error" to "method_not_allowed"), result.body)
    }

    @Test
    @DisplayName("content-type no soportado devuelve 415")
    fun handleUnsupportedMediaType() {
        val result = handler.handleGeneric(HttpMediaTypeNotSupportedException("text/plain"))

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, result.statusCode)
        assertEquals(mapOf("error" to "unsupported_media_type"), result.body)
    }

    @Test
    @DisplayName("otro 4xx de spring mantiene su status con bad_request")
    fun handleOtherClientError() {
        val result = handler.handleGeneric(ResponseStatusException(HttpStatus.CONFLICT))

        assertEquals(HttpStatus.CONFLICT, result.statusCode)
        assertEquals(mapOf("error" to "bad_request"), result.body)
    }

    @Test
    @DisplayName("5xx de spring devuelve internal_error")
    fun handleSpringServerError() {
        val result = handler.handleGeneric(ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, result.statusCode)
        assertEquals(mapOf("error" to "internal_error"), result.body)
    }

    @Test
    @DisplayName("error sin causa devuelve 500")
    fun handleGenericNoCause() {
        val result = handler.handleGeneric(RuntimeException("boom"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertEquals(mapOf("error" to "internal_error"), result.body)
    }
}
