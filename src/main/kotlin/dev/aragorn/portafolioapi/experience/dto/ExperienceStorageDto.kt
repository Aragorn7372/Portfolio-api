package dev.aragorn.portafolioapi.experience.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size


data class ExperienceStorageDto(
    @NotBlank
    @NotEmpty
    @NotNull
    @Size(max = 200)
    val id: String,

    @NotBlank
    @NotEmpty
    @NotNull
    @Size(max = 200)
    val title: String,

    @NotBlank
    @NotEmpty
    @NotNull
    @Size(max = 200)
    val company: String,

    @NotBlank
    @NotEmpty
    @NotNull
    @Size(max = 200)
    val location: String,

    @NotBlank
    @NotEmpty
    @NotNull
    val mdFile: String,

    @NotEmpty
    @NotNull
    val images: List<StorageImage>

)
