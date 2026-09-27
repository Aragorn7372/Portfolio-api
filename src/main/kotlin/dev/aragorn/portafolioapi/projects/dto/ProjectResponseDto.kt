package dev.aragorn.portafolioapi.projects.dto


/**
 * Proyecto tal y como lo devuelve `GET /projects`.
 *
 * Es la vista pública de [dev.aragorn.portafolioapi.projects.model.Project]: aplana el
 * propietario y no expone ids internos ni fechas. También es el tipo que se guarda en la caché
 * `projects`.
 *
 * @property name nombre del repositorio.
 * @property displayName nombre para mostrar en el portafolio: lo que va antes de la primera `|` de
 *   la descripción de GitHub (`<nombre> | <descripción>`), o [name] si no sigue ese formato. Por
 *   defecto vale [name], así las entradas de caché con el formato antiguo siguen siendo válidas.
 * @property description descripción sin el nombre para mostrar, si queda texto.
 * @property url URL pública del repositorio.
 * @property pagesUrl URL del sitio publicado, si tiene.
 * @property owner login del propietario.
 * @property avatarUrl avatar del propietario.
 * @property stars número de estrellas.
 * @property forks número de forks.
 * @property commits número de commits.
 * @property languages porcentaje por lenguaje.
 * @property topics temas del repositorio.
 * @property technologies tecnologías detectadas.
 */
data class ProjectResponseDto(
    val name: String,
    val displayName: String = name,
    val description: String?,
    val url: String,
    val pagesUrl: String?,
    val owner: String,
    val avatarUrl: String,
    val stars: Int,
    val forks: Int,
    val commits: Int,
    val languages: Map<String, Double>,
    val topics: List<String>,
    val technologies: List<String> = emptyList(),
)
