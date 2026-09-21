package dev.aragorn.portafolioapi.visits.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version


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
