package dev.aragorn.portafolioapi.certificates.service

import dev.aragorn.portafolioapi.certificates.client.CertificateClient
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.mapper.CertificateMapper
import dev.aragorn.portafolioapi.certificates.model.Certificates
import dev.aragorn.portafolioapi.certificates.repository.CertificatesRepository
import dev.aragorn.portafolioapi.common.service.validator.Validator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CertificateServiceImplTest{
    @Mock
    private lateinit var mapper: CertificateMapper
    @Mock
    private lateinit var repositorio: CertificatesRepository
    @Mock
    private lateinit var client: CertificateClient
    @Mock
    private lateinit var validator: Validator<CertificatesResponseDto>
    @InjectMocks
    private lateinit var service: CertificateServiceImpl
    private val certificate1 = Certificates(
        name = "Kotlin Developer",
        date = LocalDate.of(2025, 3, 15),
        url = "https://drive.google.com/file/d/kotlin-developer-123/view"
    )

    private val certificate2 = Certificates(
        name = "Spring Boot Developer",
        date = LocalDate.of(2025, 5, 20),
        url = "https://drive.google.com/file/d/spring-boot-456/view"
    )

    private val certificate3 = Certificates(
        name = "Docker Fundamentals",
        date = LocalDate.of(2025, 7, 10),
        url = "https://drive.google.com/file/d/docker-789/view"
    )

    private val certificate4 = Certificates(
        name = "Java Programming",
        date = LocalDate.of(2025, 9, 5),
        url = "https://drive.google.com/file/d/java-abc123/view"
    )
    private val certificateDto1 = CertificatesResponseDto(
        "Kotlin Developer",
        "https://drive.google.com/file/d/kotlin-developer-123/view",
        LocalDate.of(2025, 3, 15).toString()
    )

    private val certificateDto2 = CertificatesResponseDto(
         "Spring Boot Developer",
        "https://drive.google.com/file/d/spring-boot-456/view",
         LocalDate.of(2025, 5, 20).toString()
    )

    private val certificateDto3 = CertificatesResponseDto(
       "Docker Fundamentals",
        "https://drive.google.com/file/d/docker-789/view",
        LocalDate.of(2025, 7, 10).toString()
    )

    private val certificateDto4 = CertificatesResponseDto(
        "Java Programming",
        "https://drive.google.com/file/d/java-abc123/view",
        LocalDate.of(2025, 9, 5).toString()
    )

    @Test
    @DisplayName("refresh bien, todos nuevos ninguno en base de datos")
    fun refresh() = runTest {
        val certificates = listOf(
            certificate1,
            certificate2,
            certificate3
        )
        whenever(client.findCertificates())
            .thenReturn(listOf(certificateDto1,certificateDto2,certificateDto3))
        whenever(repositorio.findAll()).thenReturn(listOf())
        whenever(mapper.dtoToModel(certificateDto1)).thenReturn(certificate1)
        whenever(mapper.dtoToModel(certificateDto2)).thenReturn(certificate2)
        whenever(mapper.dtoToModel(certificateDto3)).thenReturn(certificate3)
        whenever(repositorio.saveAll(certificates))
            .thenReturn(certificates)

        service.refresh()


        verify(client, times(1)).findCertificates()

        verify(validator,times(1)).validate(certificateDto1)
        verify(validator,times(1)).validate(certificateDto2)
        verify(validator,times(1)).validate(certificateDto3)

        verify(mapper,times(1)).dtoToModel(certificateDto1)
        verify(mapper,times(1)).dtoToModel(certificateDto2)
        verify(mapper,times(1)).dtoToModel(certificateDto3)

        verify(repositorio,times(1)).findAll()

        verify(repositorio, times(0)).deleteById(any())

        verify(repositorio,times(1)).saveAll(certificates)
    }
    @Test
    @DisplayName("refresh with deleted certificates")
    fun refreshWithDeleted() = runTest {
        val certificates = listOf(
            certificate1,
            certificate2,
            certificate3
        )
        whenever(client.findCertificates())
            .thenReturn(listOf(certificateDto1,certificateDto2,certificateDto3))
        whenever(repositorio.findAll()).thenReturn(listOf(certificate4))
        whenever(mapper.dtoToModel(certificateDto1)).thenReturn(certificate1)
        whenever(mapper.dtoToModel(certificateDto2)).thenReturn(certificate2)
        whenever(mapper.dtoToModel(certificateDto3)).thenReturn(certificate3)
        whenever(repositorio.saveAll(certificates))
            .thenReturn(certificates)

        service.refresh()
        verify(client, times(1)).findCertificates()

        verify(validator,times(1)).validate(certificateDto1)
        verify(validator,times(1)).validate(certificateDto2)
        verify(validator,times(1)).validate(certificateDto3)

        verify(mapper,times(1)).dtoToModel(certificateDto1)
        verify(mapper,times(1)).dtoToModel(certificateDto2)
        verify(mapper,times(1)).dtoToModel(certificateDto3)

        verify(repositorio,times(1)).findAll()

        verify(repositorio, times(1)).deleteById(certificate4.id)

        verify(repositorio,times(1)).saveAll(certificates)
    }

    @Test
    @DisplayName("obtener todos los certificates")
    fun getAll()=runTest {
        whenever(repositorio.findAll()).thenReturn(listOf(certificate1,certificate2,certificate3))
        whenever(mapper.certificateToDto(certificate1)).thenReturn(certificateDto1)
        whenever(mapper.certificateToDto(certificate2)).thenReturn(certificateDto2)
        whenever(mapper.certificateToDto(certificate3)).thenReturn(certificateDto3)

        val result = service.getAll()

        verify(repositorio, times(1)).findAll()
        verify(mapper, times(1)).certificateToDto(certificate1)
        verify(mapper, times(1)).certificateToDto(certificate2)
        verify(mapper, times(1)).certificateToDto(certificate3)
    }

}