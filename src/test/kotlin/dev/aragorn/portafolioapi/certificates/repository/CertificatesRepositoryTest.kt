package dev.aragorn.portafolioapi.certificates.repository

import dev.aragorn.portafolioapi.BaseRepositoryTest
import dev.aragorn.portafolioapi.certificates.model.Certificates
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.context.TestConstructor
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CertificatesRepositoryTest(
    private val repository: CertificatesRepository,
    private val entityManager: EntityManager,
): BaseRepositoryTest() {
    @BeforeEach
    fun setup() {
        repository.deleteAll()
    }
    private val certificate1 = Certificates(
        name = "Kotlin Developer",
        date = LocalDate.of(2025, 3, 15),
        url = "https://drive.google.com/file/d/kotlin-developer-123/view"
    )

    private val certificate2 = Certificates(
        name = "Spring Boot Developer",
        date = LocalDate.of(2025, 5, 20),
        url = "https://drive.google.com/file/d/spring-boot-456/view"
    )

    private val certificate3 = Certificates(
        name = "Docker Fundamentals",
        date = LocalDate.of(2025, 7, 10),
        url = "https://drive.google.com/file/d/docker-789/view"
    )

    @Test
    fun testGetAllCertificates() {
        repository.save(certificate1)
        repository.save(certificate2)
        val result= repository.findAll()
        assertEquals(result.size,2,"debe contener dos certificados")
        assertNotNull(result,"no deberia ser nulo")
        assertTrue(result.isNotEmpty(),"no deberia estar vacio")
    }
    @Test
    fun testGetCertificatesEmpty() {
        val result = repository.findAll()
        assertEquals(result.size,0,"no debe tener certificados certificados")
        assertNotNull(result,"no deberia ser nulo")
        assertTrue(result.isEmpty(),"deberia estar vacio")
    }
    @Test
    fun testSaveCertificates() {
        val list = listOf(certificate1, certificate2, certificate3)
        val result=repository.saveAll(list)
        assertEquals(result.size,3,"deben contener 3 certificados")
        assertTrue(result.containsAll(list),"deberian de estar los elementos de list")
        assertTrue(result.isNotEmpty(),"no deberia estar vacio")
        assertNotNull(result,"no deberia ser nulo")
    }
    @Test
    fun testSaveAndUpdateCertificates() {
        val copy = Certificates(
            name = "Kotlin Developer advance",
            date = certificate1.date,
            url = certificate1.url,
        )
        val list = listOf(copy, certificate2, certificate3)
        repository.save(certificate1)
        val result = repository.saveAll(list)
        val certificate= result.find { it.name == "Kotlin Developer advance" }
        assertEquals(result.size,3,"deben contener 3 certificados")
        assertTrue(result.containsAll(list),"deberian de estar los elementos de list")
        assertTrue(result.isNotEmpty(),"no deberia estar vacio")
        assertNotNull(result,"no deberia ser nulo")
        assertNotNull(certificate,"deberia haberse actualizado el nombre")
        assertNotEquals(certificate1,copy, "no deberian ser iguales")
    }
    @Test
    fun testDeleteCertificates() {
        val list = listOf(certificate1, certificate2, certificate3)
        val result= repository.saveAll(list)
        repository.deleteAll()
        val getAllResult= repository.findAll()
        assertNotEquals(result.size,getAllResult.size,"no deben coincidir")
        assertTrue(result.containsAll(list),"deben contener 3 certificados")
        assertTrue(result.isNotEmpty(),"deben contener 3 certificados")
        assertFalse(getAllResult.isNotEmpty(),"no deben contener 3 certificados")

    }

}