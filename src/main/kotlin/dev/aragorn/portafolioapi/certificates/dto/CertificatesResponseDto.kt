package dev.aragorn.portafolioapi.certificates.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/**
 * Certificado en formato de intercambio.
 *
 * Tiene dos usos:
 * - **Entrada:** es el formato que devuelve el servicio externo de certificados. Antes de
 *   persistirlo se comprueban sus anotaciones con
 *   [dev.aragorn.portafolioapi.certificates.validator.CertificateValidator].
 * - **Salida:** es el formato que devuelve `GET /certificates` y el que se guarda en la caché `certificados`.
 *
 * Los nombres de los campos están en español porque son los del JSON de origen.
 *
 * @property titulo título del certificado. Obligatorio.
 * @property url enlace público al documento. Tiene que cumplir el patrón de `@Pattern` (el
 *   almacenamiento de documentos aceptado).
 * @property fecha fecha de expedición con formato `yyyy-MM-dd`.
 */
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
    @NotBlank
    @NotNull
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
    val fecha: String,
)
