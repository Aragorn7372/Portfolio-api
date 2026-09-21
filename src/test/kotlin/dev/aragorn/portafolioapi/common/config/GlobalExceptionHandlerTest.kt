package dev.aragorn.portafolioapi.common.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.web.bind.MethodArgumentNotValidException

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
    @DisplayName("error sin causa devuelve 500")
    fun handleGenericNoCause() {
        val result = handler.handleGeneric(RuntimeException("boom"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.statusCode)
        assertEquals(mapOf("error" to "internal_error"), result.body)
    }
}
