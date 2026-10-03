package dev.aragorn.portafolioapi.experience.mapper

import dev.aragorn.portafolioapi.experience.dto.ExperienceDetailsResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceImageResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceResponseDto
import dev.aragorn.portafolioapi.experience.dto.ExperienceStorageDto
import dev.aragorn.portafolioapi.experience.model.Experience
import dev.aragorn.portafolioapi.experience.model.ExperienceImage
import org.springframework.stereotype.Component

@Component
class ExperienceMapper {
    fun experienceToDto(experience: Experience): ExperienceResponseDto{
        return ExperienceResponseDto(
            id = experience.id,
            title = experience.title,
            company = experience.company,
            location = experience.location,
            companyLogo = experience.images
                .firstOrNull { it.relativePath.substringAfterLast("/") == "logo.png" }
                ?.cloudinaryUrl
        )
    }
    fun experienceToDetailsDto(experience: Experience): ExperienceDetailsResponseDto{
        return ExperienceDetailsResponseDto(
            id = experience.id,
            title = experience.title,
            company = experience.company,
            location = experience.location,
            markdown = experience.markdown,
            images = experience.images.map { imageToImageDto(it) }.toList()
        )
    }
    private fun imageToImageDto(images: ExperienceImage): ExperienceImageResponseDto{
        return ExperienceImageResponseDto(
            filename = images.filename,
            url = images.cloudinaryUrl
        )
    }
    fun experienceStorageDtotoModel(experience: ExperienceStorageDto): Experience{
        return Experience(
            id = experience.id,
            title = experience.title,
            company = experience.company,
            location = experience.location,
            markdown = experience.mdFile,
            contentHash = null,
        )
    }
}