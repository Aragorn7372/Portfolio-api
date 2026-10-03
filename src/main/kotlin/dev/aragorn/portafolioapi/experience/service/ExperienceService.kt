package dev.aragorn.portafolioapi.experience.service

import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto

interface ExperienceService {
    suspend fun refresh()
    suspend fun getExperiences(): List<ExperienceResponseDto>
    suspend fun getExperience(id: String): ExperienceDetailsResponseDto
}