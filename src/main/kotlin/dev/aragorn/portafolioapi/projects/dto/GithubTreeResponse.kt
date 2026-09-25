package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Respuesta del endpoint `GET /repos/{owner}/{repo}/git/trees/{ref}?recursive=1` de GitHub.
 *
 * @property sha SHA del árbol.
 * @property truncated `true` si GitHub recortó el árbol por ser demasiado grande. En ese caso la
 *   detección de tecnologías trabaja con una lista parcial de ficheros.
 * @property tree lista plana de todos los nodos (ficheros y directorios) del repositorio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubTreeResponse(
    val sha: String? = null,
    val truncated: Boolean = false,
    val tree: List<GithubTreeNode> = emptyList(),
)

/**
 * Nodo del árbol de un repositorio.
 *
 * @property path ruta relativa a la raíz del repositorio.
 * @property type tipo de nodo según GitHub: `blob` (fichero), `tree` (directorio) o `commit` (submódulo).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubTreeNode(
    val path: String = "",
    val type: String = "",
)
