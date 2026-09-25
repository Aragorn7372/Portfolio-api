package dev.aragorn.portafolioapi.visits.repository

import dev.aragorn.portafolioapi.visits.model.Visits
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** Repositorio JPA del contador [Visits]. */
@Repository
interface VisitsRepository : JpaRepository<Visits, Long> {
    /**
     * Suma 1 al contador directamente en base de datos (`UPDATE ... SET total = total + 1`).
     *
     * Es atómico y no pasa por el bloqueo optimista. Se usa como último recurso cuando
     * [dev.aragorn.portafolioapi.visits.service.VisitsServiceImpl] agota sus reintentos.
     *
     * @return número de filas actualizadas: `1` si existe el contador y `0` si no.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Visits v SET v.total = v.total + 1 WHERE v.id = 1")
    fun increment(): Int
}
