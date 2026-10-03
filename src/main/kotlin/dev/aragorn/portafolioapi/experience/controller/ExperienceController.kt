package dev.aragorn.portafolioapi.experience.controller

import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.service.ExperienceService
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

@RestController
@RequestMapping("/experiences")
class ExperienceController(
    private val service: ExperienceService
) {
    private val logger = Logger.getLogger(ExperienceController::class.java.name)
    @GetMapping("","/")
    suspend fun getAllExperiences(): ResponseEntity<List<ExperienceResponseDto>> {
        logger.info("Getting all experiences")
        return ResponseEntity.ok(
            withTimeout(5000){
                service.getExperiences()
            }
        )
    }
    @GetMapping("/{id}","/{id}/")
    suspend fun getExperienceById(@PathVariable("id") id: String)
    : ResponseEntity<ExperienceDetailsResponseDto> {
        logger.info("Getting experience by id: $id")
        return ResponseEntity.ok(
            withTimeout(5000){
                service.getExperience(id)
            }
        )
    }
}