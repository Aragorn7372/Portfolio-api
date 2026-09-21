package dev.aragorn.portafolioapi.projects.controller

import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto
import dev.aragorn.portafolioapi.projects.service.GithubService
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger


@RestController
@RequestMapping("/projects")
class ProjectsController(
    private val githubService: GithubService,
) {

    private val logger = Logger.getLogger(ProjectsController::class.java.name)

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
