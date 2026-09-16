package dev.aragorn.portafolioapi.certificates.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDate

data class CertificatesResponseDto(
    @NotBlank
    @NotEmpty
    @NotNull
    val titulo: String,
    @NotBlank
    @NotEmpty
    @NotNull
    @Pattern(regexp = "https://drive\\.google\\.com/.*")
    val url: String,
    @NotNull
    val fecha: LocalDate,
)
