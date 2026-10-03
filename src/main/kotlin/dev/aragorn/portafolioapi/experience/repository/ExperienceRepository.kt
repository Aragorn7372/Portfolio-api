package dev.aragorn.portafolioapi.experience.repository

import dev.aragorn.portafolioapi.experience.model.Experience
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Las imágenes se cargan con la propia consulta ([EntityGraph]): con `open-in-view=false`
 * y servicios `suspend` no hay sesión abierta cuando el mapper las recorre, y una
 * colección lazy lanzaría `LazyInitializationException`.
 */
@Repository
interface ExperienceRepository: JpaRepository<Experience, String>{
    @EntityGraph(attributePaths = ["images"])
    override fun findAll(): MutableList<Experience>

    @EntityGraph(attributePaths = ["images"])
    fun findExperienceById(id: String): Experience?
}
