package dev.aragorn.portafolioapi.projects.mapper

/**
 * Nombre para mostrar y descripción extraídos de la descripción de GitHub.
 *
 * @property displayName nombre que se enseña en el portafolio.
 * @property description descripción sin el nombre, o `null` si no queda texto.
 */
data class ParsedDescription(
    val displayName: String,
    val description: String?,
)

/**
 * Saca el nombre para mostrar de un proyecto a partir de su descripción de GitHub.
 *
 * El formato es `<nombre bonito> | <descripción>`: lo que va antes de la **primera** `|` es el
 * nombre y el resto es la descripción (las demás `|` se conservan). Si la descripción no sigue el
 * formato (no hay, no tiene `|`, la parte del nombre está vacía o es demasiado larga para ser un
 * nombre) se usa el nombre del repositorio y la descripción se deja tal cual.
 *
 * Se aplica al construir la respuesta, no al guardar: la base de datos conserva la descripción
 * original de GitHub.
 */
object DisplayNameParser {

    /** Longitud máxima del nombre: más larga, se asume que la `|` era parte de la descripción. */
    const val MAX_DISPLAY_NAME_LENGTH = 80

    private val PATTERN = Regex("""^\s*([^|]+?)\s*\|\s*(.*?)\s*$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * @param repoName nombre del repositorio, usado cuando la descripción no trae nombre.
     * @param description descripción cruda de GitHub.
     * @return el nombre para mostrar y la descripción que queda.
     */
    fun parse(repoName: String, description: String?): ParsedDescription {
        val text = description?.trim()
        if (text.isNullOrEmpty()) return ParsedDescription(repoName, null)

        val match = PATTERN.matchEntire(text)
        if (match == null) {
            // Sin "|" o con el nombre vacío ("| descripción"): solo se quita la barra inicial
            val rest = text.removePrefix("|").trim()
            return ParsedDescription(repoName, rest.ifEmpty { null })
        }

        val (name, rest) = match.destructured
        if (name.length > MAX_DISPLAY_NAME_LENGTH) return ParsedDescription(repoName, text)
        return ParsedDescription(name, rest.ifEmpty { null })
    }
}
