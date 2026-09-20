package dev.aragorn.portafolioapi.visits.repository

import dev.aragorn.portafolioapi.visits.model.Visits
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface VisitsRepository : JpaRepository<Visits, Long> {
    /** Incremento atómico (una sentencia, bloqueo de fila). Devuelve filas afectadas. */
    @Modifying
    @Transactional
    @Query("UPDATE Visits v SET v.total = v.total + 1 WHERE v.id = 1")
    fun increment(): Int
}
