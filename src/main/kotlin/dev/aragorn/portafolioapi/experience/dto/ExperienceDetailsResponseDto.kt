package dev.aragorn.portafolioapi.experience.dto

data class ExperienceDetailsResponseDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val markdown: String,
    val images: List<ExperienceImageResponseDto>
)
