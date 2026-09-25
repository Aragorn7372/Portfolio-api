package dev.aragorn.portafolioapi.certificates.controller

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.service.CertificateService
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

/**
 * Endpoint público de certificados del portafolio.
 *
 * Solo lee datos ya guardados: nunca llama al servicio externo durante una petición. La ruta está
 * protegida por las reglas de `app.gate.rules`, así que por defecto exige un token de visita (ver
 * [dev.aragorn.portafolioapi.visits.gate.VisitJwtFilter]).
 *
 * @param certificatesService servicio de certificados.
 */
@RestController
@RequestMapping("/certificates")
class CertificatesController(
    private val certificatesService: CertificateService
) {

    private val logger = Logger.getLogger(CertificatesController::class.java.name)

    /**
     * `GET /certificates`: lista todos los certificados.
     *
     * Respuestas:
     * - `200`: array JSON de [CertificatesResponseDto] (`titulo`, `url`, `fecha`).
     * - `401 {"error":"visit_token_required"}`: falta el token de visita o no es válido.
     * - `429 {"error":"rate_limited"}`: se ha superado el límite de peticiones por minuto.
     * - `500 {"error":"internal_error"}`: la lectura ha tardado más de 1 s u otro error inesperado.
     *
     * @return la lista de certificados.
     */
    @GetMapping("", "/")
    suspend fun getAllCertificates(): ResponseEntity<List<CertificatesResponseDto>> {
        logger.info("Getting certificates")
        return ResponseEntity.ok(
            withTimeout(1000){
                certificatesService.getAll()
            }
        )
    }
}