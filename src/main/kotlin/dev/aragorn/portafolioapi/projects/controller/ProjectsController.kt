package dev.aragorn.portafolioapi.projects.controller

import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.service.GithubService
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger


/**
 * Endpoint público de proyectos del portafolio.
 *
 * Solo lee datos ya guardados: nunca llama a GitHub durante una petición. La ruta está protegida
 * por las reglas de `app.gate.rules`, así que por defecto exige un token de visita (ver
 * [dev.aragorn.portafolioapi.visits.gate.VisitJwtFilter]).
 *
 * @param githubService servicio de proyectos.
 */
@RestController
@RequestMapping("/projects")
class ProjectsController(
    private val githubService: GithubService,
) {

    private val logger = Logger.getLogger(ProjectsController::class.java.name)

    /**
     * `GET /projects`: lista todos los proyectos.
     *
     * Respuestas:
     * - `200`: array JSON de [ProjectResponseDto].
     * - `401 {"error":"visit_token_required"}`: falta el token de visita o no es válido.
     * - `429 {"error":"rate_limited"}`: se ha superado el límite de peticiones por minuto.
     * - `500 {"error":"internal_error"}`: la lectura ha tardado más de 5 s u otro error inesperado.
     *
     * @return la lista de proyectos.
     */
    @GetMapping("", "/")
    suspend fun getAllProjects(): ResponseEntity<List<ProjectResponseDto>> {
        logger.info("Getting projects")
        return ResponseEntity.ok(
            withTimeout(5_000) {
                githubService.getProjects()
            },
        )
    }
}
