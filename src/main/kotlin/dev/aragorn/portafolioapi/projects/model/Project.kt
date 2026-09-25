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

/**
 * Proyecto del portafolio: la foto de un repositorio de GitHub en el último refresco.
 *
 * La tabla funciona como una réplica local: cada refresco sustituye su contenido por completo
 * (ver [dev.aragorn.portafolioapi.projects.service.ProjectPersistenceService.replaceAll]). Así el
 * endpoint de proyectos nunca llama a GitHub en tiempo de petición.
 *
 * Las colecciones (`topics`, `languages`, `technologies`) se guardan como columnas `jsonb`.
 *
 * @property id id numérico del repositorio en GitHub. Es la clave primaria y no se autogenera.
 * @property name nombre corto del repositorio.
 * @property fullName nombre completo `propietario/repositorio`.
 * @property description descripción del repositorio, si tiene.
 * @property url URL pública del repositorio.
 * @property owner propietario (carga perezosa; ver
 *   [dev.aragorn.portafolioapi.projects.repository.ProjectsRepository.findAll]).
 * @property stars número de estrellas.
 * @property forks número de forks.
 * @property commits número de commits de la rama por defecto (0 si no se pudo obtener).
 * @property pagesUrl URL del sitio publicado con GitHub Pages, si existe.
 * @property fork indica si el repositorio es un fork de otro.
 * @property topics temas (topics) declarados en el repositorio.
 * @property createdAt fecha de creación en GitHub.
 * @property updatedAt fecha de la última actualización de metadatos en GitHub.
 * @property pushedAt fecha del último push.
 * @property languages porcentaje de código por lenguaje (0–100, con un decimal).
 * @property technologies tecnologías detectadas por
 *   [dev.aragorn.portafolioapi.projects.detector.TechStackDetector], ordenadas alfabéticamente.
 */
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