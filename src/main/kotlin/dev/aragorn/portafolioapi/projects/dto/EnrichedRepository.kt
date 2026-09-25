package dev.aragorn.portafolioapi.projects.dto

import kotlin.math.round


/**
 * Repositorio de GitHub con los datos extra que se consultan aparte del listado.
 *
 * Es el resultado intermedio del refresco: lo produce
 * [dev.aragorn.portafolioapi.projects.service.GithubServiceImpl] y lo convierte a entidad
 * [dev.aragorn.portafolioapi.projects.mapper.GithubMapper.toProject].
 *
 * @property repository datos básicos del listado de repositorios.
 * @property languages porcentaje por lenguaje, ya calculado con [toPercentages].
 * @property commits número de commits de la rama por defecto.
 * @property pagesUrl URL de GitHub Pages, o `null` si no tiene.
 * @property technologies tecnologías detectadas en los ficheros del repositorio.
 */
data class EnrichedRepository(
    val repository: GithubRepositoryResponse,
    val languages: Map<String, Double>,
    val commits: Int,
    val pagesUrl: String?,
    val technologies: List<String> = emptyList(),
)

/**
 * Convierte los bytes por lenguaje que devuelve GitHub en porcentajes con un decimal.
 *
 * Ejemplo: `{"Kotlin": 750, "Shell": 250}` → `{"Kotlin": 75.0, "Shell": 25.0}`.
 * Por el redondeo, la suma puede no dar exactamente 100.
 *
 * @receiver mapa lenguaje → bytes de código.
 * @return mapa lenguaje → porcentaje (0–100), o un mapa vacío si el total es 0.
 */
fun Map<String, Long>.toPercentages(): Map<String, Double> {
    val total = values.sum().toDouble()
    if (total <= 0) return emptyMap()
    return mapValues { (_, bytes) -> round(bytes * 1000.0 / total) / 10.0 }
}
