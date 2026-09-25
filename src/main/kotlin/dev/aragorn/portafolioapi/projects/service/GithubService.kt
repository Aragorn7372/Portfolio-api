package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto

/**
 * Casos de uso del módulo de proyectos.
 *
 * La lectura y la escritura van separadas: [refresh] es el único que habla con GitHub, y
 * [getProjects] solo lee de la base de datos (a través de la caché).
 *
 * @see GithubServiceImpl
 */
interface GithubService {
    /**
     * Descarga de GitHub los repositorios configurados, los enriquece y sustituye con ellos los
     * proyectos guardados.
     *
     * @throws dev.aragorn.portafolioapi.projects.client.GithubException si falla el listado o se agota la cuota.
     * @throws IllegalArgumentException si no hay usuario configurado.
     */
    suspend fun refresh()

    /**
     * Devuelve los proyectos guardados.
     *
     * @return todos los proyectos como DTO público.
     */
    suspend fun getProjects(): List<ProjectResponseDto>
}
