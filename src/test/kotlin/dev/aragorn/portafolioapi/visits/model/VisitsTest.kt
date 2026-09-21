package dev.aragorn.portafolioapi.visits.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class VisitsTest {
    private val visit1 = Visits()

    @Test
    @DisplayName("defaults de fila única")
    fun defaults() {
        assertEquals(1L, visit1.id)
        assertEquals(0L, visit1.total)
        assertEquals(0L, visit1.version)
    }

    @Test
    @DisplayName("copy incrementa el total")
    fun increment() {
        val next = visit1.copy(total = visit1.total + 1)

        assertEquals(1L, next.total)
        assertEquals(0L, visit1.total)
    }
}
