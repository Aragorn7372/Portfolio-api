package dev.aragorn.portafolioapi.visits.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version


/**
 * Contador global de visitas del portafolio.
 *
 * La tabla tiene una sola fila, siempre con `id = 1`, que crea al arrancar
 * [dev.aragorn.portafolioapi.visits.startup.VisitsStartupSeed].
 *
 * @property id identificador fijo de la fila (siempre `1`).
 * @property total número de visitas únicas contadas.
 * @property version versión para el bloqueo optimista (`@Version`). Si dos instancias
 *   incrementan a la vez, una falla y reintenta, así no se pierde ningún incremento.
 */
@Entity
@Table(name = "visits")
data class Visits(
    @Id
    val id: Long = 1,
    @Column(nullable = false)
    val total: Long = 0L,
    @Version
    var version: Long = 0L,
)
