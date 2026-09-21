package dev.aragorn.portafolioapi.projects.repository

import dev.aragorn.portafolioapi.BaseRepositoryTest
import dev.aragorn.portafolioapi.projects.model.Owner
import dev.aragorn.portafolioapi.projects.model.Project
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.TestConstructor
import kotlin.test.Test
import kotlin.test.assertTrue

@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProjectsRepositoryTest(
    private val repository: ProjectsRepository,
    private val owners: OwnersRepository,
    private val entityManager: EntityManager,
) : BaseRepositoryTest() {
    @BeforeEach
    fun setup() {
        repository.deleteAll()
        owners.deleteAll()
    }

    private val owner1 = Owner(name = "Aragorn7372", avatarUrl = "https://avatars.github.com/u/1")

    private fun project(id: Long, name: String, owner: Owner) = Project(
        id = id,
        name = name,
        fullName = "Aragorn7372/$name",
        description = "$name repo",
        url = "https://github.com/Aragorn7372/$name",
        owner = owner,
        stars = 12,
        forks = 4,
        commits = 342,
        languages = mapOf("Kotlin" to 100.0),
        topics = listOf("kotlin"),
    )

    @Test
    fun testSaveAndFindAll() {
        val owner = owners.save(owner1)
        repository.saveAll(listOf(project(1L, "demo", owner), project(2L, "otro", owner)))

        val result = repository.findAll()

        assertEquals(result.size, 2, "debe contener dos proyectos")
        assertNotNull(result, "no deberia ser nulo")
        assertTrue(result.isNotEmpty(), "no deberia estar vacio")
    }

    @Test
    fun testFindAllEagerOwner() {
        val owner = owners.save(owner1)
        repository.save(project(1L, "demo", owner))
        entityManager.flush()
        entityManager.clear()

        val result = repository.findAll()
        entityManager.clear()

        assertEquals(result.size, 1, "debe contener un proyecto")
        assertEquals("Aragorn7372", result[0].owner.name, "debe traer el owner sin lazy")
    }

    @Test
    fun testDeleteAll() {
        val owner = owners.save(owner1)
        val project1 = project(1L, "demo", owner)
        val project2 = project(2L, "otro", owner)
        val project3 = project(3L, "viejo", owner)
        repository.saveAll(listOf(project1, project2, project3))

        repository.deleteAll(listOf(project3))
        val result = repository.findAll()

        assertEquals(result.size, 2, "deben quedar dos proyectos")
        assertTrue(result.containsAll(listOf(project1, project2)), "deben estar los no borrados")
    }
}
