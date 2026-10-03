package dev.aragorn.portafolioapi.experience.storage

import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import java.nio.file.Path

fun interface StorageExperienceZip {
    suspend fun readZip(zip: Path): List<ExperienceStorageDto>
}