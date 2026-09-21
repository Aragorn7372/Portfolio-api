package dev.aragorn.portafolioapi.certificates.mapper

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.exceptions.InvalidCertificateException
import dev.aragorn.portafolioapi.certificates.model.Certificates
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate


class CertificateMapperTest() {
    private val mapper = CertificateMapper()
    private val certificate1 = Certificates(
        name = "Kotlin Developer",
        date = LocalDate.of(2025, 3, 15),
        url = "https://drive.google.com/file/d/kotlin-developer-123/view"
    )
    private val certificate2 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        LocalDate.of(2025, 3, 15).toString()
    )
    private val certificate3 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        "buenasnoches"
    )
    @Test
    fun certificateToDto() {
        val result=mapper.certificateToDto( certificate1)
        assertEquals(certificate2,result)
    }

    @Test
    fun dtoToModel() {
        val result= mapper.dtoToModel(certificate2)
        assertEquals(certificate1,result)
    }
    @Test
    @DisplayName("to model con fecha incorrecta")
    fun dtoToModelBad(){
        val exception = assertThrows<InvalidCertificateException> {
            mapper.dtoToModel(certificate3)
        }

        assertEquals(
            "fecha inválida 'buenasnoches' en 'Kotlin Developer'",
            exception.message
        )
    }

}