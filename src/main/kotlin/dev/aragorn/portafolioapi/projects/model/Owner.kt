package dev.aragorn.portafolioapi.projects.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.validator.constraints.Length


@Entity
@Table(name = "owner")
data class Owner(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    val id: Long = 0,
    @Column(nullable = false, unique = true)
    @Length(max = 200)
    val name: String,
    @Column(nullable = false)
    @Length(max = 200)
    val avatarUrl: String,
    )
