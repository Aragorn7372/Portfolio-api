package dev.aragorn.portafolioapi.projects.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "project")
data class Project(

    @Id
    @Column(name = "id")
    val id: Long,

    @Column(nullable = false)
    val name: String,

    @Column(name = "full_name", nullable = false)
    val fullName: String,

    val description: String?,

    @Column(nullable = false)
    val url: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: Owner,

    @Column(nullable = false)
    val stars: Int,

    @Column(nullable = false)
    val forks: Int,

    @Column(nullable = false)
    val commits: Int,

    @Column(name = "pages_url")
    val pagesUrl: String? = null,

    @Column(nullable = false)
    val fork: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb", nullable = false)
    val topics: List<String> = emptyList(),

    @Column(name = "created_at")
    val createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at")
    val updatedAt: OffsetDateTime? = null,

    @Column(name = "pushed_at")
    val pushedAt: OffsetDateTime? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "languages", columnDefinition = "jsonb", nullable = false)
    val languages: Map<String, Double>,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "technologies", columnDefinition = "jsonb", nullable = false)
    val technologies: List<String> = emptyList(),

)