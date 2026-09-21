package dev.aragorn.portafolioapi.certificates.controller

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.service.CertificateService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
class CertificatesControllerTest {
    @Mock
    private lateinit var certificatesService: CertificateService

    @InjectMocks
    private lateinit var controller: CertificatesController

    private val certificateDto1 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        "2025-03-15"
    )
    private val certificateDto2 = CertificatesResponseDto(
        "Spring Boot Developer",
        "https://drive.google.com/file/d/spring-boot-456/view",
        "2025-05-20"
    )

    @Test
    @DisplayName("obtener todos los certificates")
    fun getAllCertificates() = runTest {
        whenever(certificatesService.getAll()).thenReturn(listOf(certificateDto1, certificateDto2))

        val result = controller.getAllCertificates()

        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(listOf(certificateDto1, certificateDto2), result.body)
        verify(certificatesService, times(1)).getAll()
    }
}
