package dev.aragorn.portafolioapi.experience.repository

import dev.aragorn.portafolioapi.BaseRepositoryTest
import dev.aragorn.portafolioapi.experience.model.Experience
import dev.aragorn.portafolioapi.experience.model.ExperienceImage
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.TestConstructor
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.Test
import kotlin.test.assertTrue

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ExperienceRepositoryTest(
    private val repository: ExperienceRepository,
    private val entityManager: EntityManager,
) : BaseRepositoryTest() {
    @BeforeEach
    fun setup() {
        repository.deleteAll()
    }

    private fun experience(id: String = "acme", company: String = "Acme") = Experience(
        id = id,
        title = "Backend Developer",
        company = company,
        location = "Madrid",
        markdown = "# Backend Developer",
        contentHash = null
    )

    private fun Experience.withImage(path: String): Experience {
        val name = path.substringAfterLast("/")
        images.add(
            ExperienceImage(
                experience = this,
                filename = name,
                relativePath = path,
                cloudinaryPublicId = "${name.substringBeforeLast(".")}-01JTEST",
                cloudinaryUrl = "https://cdn.test/$name",
                contentHash = "hash-$name"
            )
        )
        return this
    }

    private fun countImages(): Long =
        entityManager.createQuery("select count(i) from ExperienceImage i", java.lang.Long::class.java)
            .singleResult.toLong()

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun testFindExperienceById() {
        repository.save(experience())
        flushAndClear()

        val result = repository.findExperienceById("acme")

        assertNotNull(result, "deberia encontrar la experiencia")
        assertEquals("Acme", result!!.company, "deberia ser la de Acme")
    }

    @Test
    fun testFindExperienceByIdNotFound() {
        val result = repository.findExperienceById("no-existe")

        assertNull(result, "no deberia encontrar nada")
    }

    @Test
    fun testGetAllExperiences() {
        repository.save(experience())
        repository.save(experience(id = "globex", company = "Globex"))

        val result = repository.findAll()

        assertEquals(2, result.size, "debe contener dos experiencias")
    }

    @Test
    fun testSaveWithImagesCascade() {
        repository.save(experience().withImage("acme/logo.png").withImage("acme/foto.jpg"))
        flushAndClear()

        val result = repository.findExperienceById("acme")

        assertNotNull(result, "deberia encontrar la experiencia")
        assertEquals(
            listOf("acme/logo.png", "acme/foto.jpg").sorted(),
            result!!.images.map { it.relativePath }.sorted(),
            "deberia guardar las imagenes en cascada"
        )
        assertTrue(result.images.all { it.id != null }, "las imagenes deberian tener id generado")
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testImagesLoadedWithoutSession() {
        // Sin transacción, como en producción (open-in-view=false + servicios suspend):
        // si images fuese lazy sin EntityGraph, acceder a ellas lanzaría LazyInitializationException.
        repository.save(experience().withImage("acme/logo.png"))
        try {
            val all = repository.findAll()
            assertEquals(listOf("acme/logo.png"), all[0].images.map { it.relativePath }, "findAll deberia traer las imagenes")

            val byId = repository.findExperienceById("acme")!!
            assertEquals(listOf("acme/logo.png"), byId.images.map { it.relativePath }, "findExperienceById deberia traer las imagenes")
        } finally {
            repository.deleteAll()
        }
    }

    @Test
    fun testOrphanRemoval() {
        repository.save(experience().withImage("acme/logo.png").withImage("acme/foto.jpg"))
        flushAndClear()

        val actual = repository.findExperienceById("acme")!!
        actual.images.removeIf { it.relativePath == "acme/foto.jpg" }
        repository.save(actual)
        flushAndClear()

        val result = repository.findExperienceById("acme")!!
        assertEquals(listOf("acme/logo.png"), result.images.map { it.relativePath }, "solo deberia quedar el logo")
        assertEquals(1L, countImages(), "la imagen huerfana deberia borrarse")
    }

    @Test
    fun testDeleteExperienceDeletesImages() {
        repository.save(experience().withImage("acme/logo.png").withImage("acme/foto.jpg"))
        flushAndClear()

        repository.delete(repository.findExperienceById("acme")!!)
        flushAndClear()

        assertNull(repository.findExperienceById("acme"), "no deberia existir la experiencia")
        assertEquals(0L, countImages(), "no deberian quedar imagenes")
    }
}
