package dev.aragorn.portafolioapi.certificates.controller

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.service.CertificateService
import kotlinx.coroutines.withTimeout
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger

@RestController
@RequestMapping("/certificates")
class CertificatesController(
    private val certificatesService: CertificateService
) {

    private val logger = Logger.getLogger(CertificatesController::class.java.name)
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