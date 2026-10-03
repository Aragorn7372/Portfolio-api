package dev.aragorn.portafolioapi.experience.model

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.validator.constraints.Length
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "experience")
data class Experience(

    @Column(nullable = false)
    @Length(max = 200)
    var title: String,

    @Column(nullable = false)
    @Length(max = 200)
    var company: String,

    @Column(nullable = false)
    @Length(max = 200)
    var location: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var markdown: String,

    @Column(name = "content_hash")
    var contentHash: String?,

    @Column(name = "synced_at")
    @LastModifiedDate
    var syncedAt: Instant? = null,

    @Id
    @Column(nullable = false)
    val id: String
) {
    // Fuera del constructor para que equals/hashCode/toString no recorran las imágenes,
    // que a su vez apuntan a esta experiencia (si no, StackOverflowError).
    @OneToMany(
        mappedBy = "experience",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val images: MutableList<ExperienceImage> = mutableListOf()
}
