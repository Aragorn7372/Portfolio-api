package dev.aragorn.portafolioapi.projects.client

import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse

/**
 * Acceso de solo lectura a la API REST de GitHub.
 *
 * Los métodos de detalle (lenguajes, commits, Pages, árbol, contenido) devuelven un valor vacío
 * cuando el recurso no existe, en lugar de lanzar una excepción. Los errores de verdad (cuota
 * agotada, credenciales, fallos del servidor) se lanzan como subclases de [GithubException].
 *
 * @see GithubClientImpl
 */
interface GithubClient {

    /**
     * Lista todos los repositorios públicos de un usuario, recorriendo todas las páginas.
     *
     * @param username login del usuario.
     * @return todos sus repositorios.
     * @throws GithubException si la API falla.
     */
    suspend fun findUserRepositories(
        username: String,
    ): List<GithubRepositoryResponse>

    /**
     * Lista todos los repositorios de una organización, recorriendo todas las páginas.
     *
     * @param organization login de la organización.
     * @return todos sus repositorios.
     * @throws GithubException si la API falla.
     */
    suspend fun findOrganizationRepositories(
        organization: String,
    ): List<GithubRepositoryResponse>

    /**
     * Bytes de código por lenguaje de un repositorio.
     *
     * @param owner propietario.
     * @param repository nombre del repositorio.
     * @return mapa lenguaje → bytes, vacío si el repositorio no existe.
     */
    suspend fun findLanguages(
        owner: String,
        repository: String,
    ): Map<String, Long>

    /**
     * Número total de commits de la rama por defecto.
     *
     * @param owner propietario.
     * @param repository nombre del repositorio.
     * @return número de commits, o `0` si el repositorio está vacío o no existe.
     */
    suspend fun findCommitCount(
        owner: String,
        repository: String,
    ): Int

    /**
     * URL del sitio de GitHub Pages del repositorio.
     *
     * @param owner propietario.
     * @param repository nombre del repositorio.
     * @return la URL, o `null` si Pages no está activado.
     */
    suspend fun findPagesUrl(
        owner: String,
        repository: String,
    ): String?

    /**
     * Rutas de todos los ficheros y directorios del repositorio en una referencia dada.
     *
     * @param owner propietario.
     * @param repository nombre del repositorio.
     * @param ref rama, tag o SHA.
     * @return lista de rutas relativas, vacía si no existe. Puede ser parcial si el árbol es muy grande.
     */
    suspend fun findRepositoryTree(
        owner: String,
        repository: String,
        ref: String,
    ): List<String>

    /**
     * Contenido en texto plano de un fichero del repositorio.
     *
     * @param owner propietario.
     * @param repository nombre del repositorio.
     * @param path ruta del fichero dentro del repositorio.
     * @param ref rama, tag o SHA.
     * @return el contenido, o `null` si no existe o está vacío.
     */
    suspend fun findFileContent(
        owner: String,
        repository: String,
        path: String,
        ref: String,
    ): String?
}
