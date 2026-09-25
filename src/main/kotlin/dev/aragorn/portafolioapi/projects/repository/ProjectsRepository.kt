package dev.aragorn.portafolioapi.projects.repository

import dev.aragorn.portafolioapi.projects.model.Project
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Repositorio JPA de [Project]. La clave primaria es el id del repositorio en GitHub. */
@Repository
interface ProjectsRepository : JpaRepository<Project, Long> {

    /** Trae el owner en la misma query: evita LazyInitializationException al mapear al DTO. */
    @EntityGraph(attributePaths = ["owner"])
    override fun findAll(): List<Project>
}
