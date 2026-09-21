package dev.aragorn.portafolioapi.visits.repository

import dev.aragorn.portafolioapi.BaseRepositoryTest
import dev.aragorn.portafolioapi.visits.model.Visits
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.TestConstructor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class VisitsRepositoryTest(
    private val repository: VisitsRepository,
    private val entityManager: EntityManager,
) : BaseRepositoryTest() {
    @BeforeEach
    fun setup() {
        repository.deleteAll()
    }

    private val visit7 = Visits(id = 1, total = 7)

    @Test
    fun testIncrement() {
        repository.save(visit7)
        entityManager.flush()
        entityManager.clear()

        val rows = repository.increment()
        entityManager.clear()
        val result = repository.findById(1)

        assertEquals(1, rows, "debe afectar a una fila")
        assertTrue(result.isPresent, "deberia existir la fila")
        assertEquals(8L, result.get().total, "debe incrementar el total")
    }

    @Test
    fun testIncrementMissingRow() {
        val rows = repository.increment()

        assertEquals(0, rows, "sin fila no debe afectar a ninguna")
    }

    @Test
    fun testSaveAndFind() {
        repository.save(visit7)
        val result = repository.findById(1)

        assertTrue(result.isPresent, "deberia existir la fila")
        assertNotNull(result.get(), "no deberia ser nulo")
        assertEquals(7L, result.get().total, "debe contener el total guardado")
    }

    @Test
    fun testExists() {
        assertFalse(repository.existsById(1), "no deberia existir la fila")

        repository.save(visit7)

        assertTrue(repository.existsById(1), "deberia existir la fila")
    }
}
