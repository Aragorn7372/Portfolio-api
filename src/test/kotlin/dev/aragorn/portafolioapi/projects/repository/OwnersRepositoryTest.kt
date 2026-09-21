package dev.aragorn.portafolioapi.projects.repository

import dev.aragorn.portafolioapi.BaseRepositoryTest
import dev.aragorn.portafolioapi.projects.model.Owner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.TestConstructor
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OwnersRepositoryTest(
    private val repository: OwnersRepository,
) : BaseRepositoryTest() {
    @BeforeEach
    fun setup() {
        repository.deleteAll()
    }

    private val owner1 = Owner(name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1")

    @Test
    fun testSaveAndFindByName() {
        repository.save(owner1)

        val result = assertNotNull(repository.findByName("Aragorn7372"), "no deberia ser nulo")

        assertEquals("Aragorn7372", result.name, "debe contener el owner guardado")
        assertEquals("https://avatars.github.com/u/1", result.avatarUrl, "debe contener el avatar guardado")
    }

    @Test
    fun testFindByNameMissing() {
        val result = repository.findByName("nadie")

        assertNull(result, "deberia ser nulo")
    }

    @Test
    fun testSaveExisting() {
        val saved = repository.save(owner1)
        repository.save(saved.copy(avatarUrl = "https://avatars.github.com/u/new"))

        val result = assertNotNull(repository.findByName("Aragorn7372"), "no deberia ser nulo")

        assertTrue(result.id != 0L, "deberia conservar el id")
        assertEquals(
            "https://avatars.github.com/u/new",
            result.avatarUrl,
            "deberia haberse actualizado el avatar"
        )
    }
}
