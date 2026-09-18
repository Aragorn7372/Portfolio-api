package dev.aragorn.portafolioapi.certificates.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

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
    // Fecha como String crudo de la API (ISO yyyy-MM-dd). El mapper la parsea
    // a LocalDate: así Jackson no necesita el módulo jsr310.
    @NotBlank
    @NotNull
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    val fecha: String,
)
