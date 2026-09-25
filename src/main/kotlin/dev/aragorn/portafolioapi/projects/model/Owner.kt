package dev.aragorn.portafolioapi.projects.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.validator.constraints.Length


/**
 * Propietario de uno o varios proyectos: un usuario u organización de GitHub.
 *
 * Se guarda en su propia tabla para no repetir el nombre y el avatar en cada proyecto. Lo crea
 * o actualiza [dev.aragorn.portafolioapi.projects.service.ProjectPersistenceService] durante el
 * refresco.
 *
 * @property id identificador interno autogenerado. No es el id de GitHub.
 * @property name login del usuario u organización en GitHub. Es único.
 * @property avatarUrl URL de la imagen de perfil. Se actualiza si cambia en origen.
 */
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
