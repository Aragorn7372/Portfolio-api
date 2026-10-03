package dev.aragorn.portafolioapi.experience.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ExperienceJsonDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    @JsonProperty("md_file")
    val mdFile: String,
    val images: List<String>
)
