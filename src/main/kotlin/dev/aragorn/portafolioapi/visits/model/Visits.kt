package dev.aragorn.portafolioapi.visits.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

/**
 * Contador global único: una sola fila (id = 1).
 * En disco solo vive un entero: ni IPs, ni hashes, ni fechas por visitante.
 * El incremento es leer-modificar-escribir con bloqueo optimista (@Version):
 * si dos hilos chocan, el perdedor reintenta con recarga (ver VisitsServiceImpl).
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
