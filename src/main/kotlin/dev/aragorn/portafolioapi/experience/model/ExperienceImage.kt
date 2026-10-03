package dev.aragorn.portafolioapi.experience.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "experience_image")
data class ExperienceImage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name= "experience_id", nullable = false)
    val experience: Experience,
    @Column(nullable = false)
    val filename: String,

    @Column(name = "relative_path", nullable = false)
    val relativePath: String,

    @Column(name = "cloudinary_public_id", nullable = false)
    val cloudinaryPublicId: String,

    @Column(name = "cloudinary_url", nullable = false)
    var cloudinaryUrl: String,

    @Column(name = "content_hash")
    var contentHash: String

)

