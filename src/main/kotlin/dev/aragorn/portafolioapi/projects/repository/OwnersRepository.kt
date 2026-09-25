package dev.aragorn.portafolioapi.projects.repository

import dev.aragorn.portafolioapi.projects.model.Owner
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Repositorio JPA de [Owner]. */
@Repository
interface OwnersRepository : JpaRepository<Owner, Long> {
    /**
     * Busca un propietario por su login de GitHub.
     *
     * @param name login exacto.
     * @return el propietario, o `null` si todavía no existe.
     */
    fun findByName(name: String): Owner?
}