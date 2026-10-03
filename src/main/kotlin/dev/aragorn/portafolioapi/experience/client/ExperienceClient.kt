package dev.aragorn.portafolioapi.experience.client

import java.nio.file.Path

interface ExperienceClient {
    suspend fun getExperience(): Path
}