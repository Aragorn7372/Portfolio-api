package dev.aragorn.portafolioapi.experience.dto

data class ExperienceResponseDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val companyLogo: String?
)
