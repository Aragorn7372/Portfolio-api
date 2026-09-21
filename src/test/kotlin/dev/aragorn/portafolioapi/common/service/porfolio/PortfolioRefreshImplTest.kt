package dev.aragorn.portafolioapi.common.service.porfolio

import dev.aragorn.portafolioapi.certificates.service.CertificateService
import dev.aragorn.portafolioapi.projects.service.GithubService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class PortfolioRefreshImplTest {
    @Mock
    private lateinit var certificatesService: CertificateService

    @Mock
    private lateinit var githubService: GithubService

    @InjectMocks
    private lateinit var service: PortfolioRefreshImpl

    @Test
    @DisplayName("refreshCertificates bien, delega en certificatesService")
    fun refreshCertificates() = runTest {
        service.refreshCertificates()

        verify(certificatesService, times(1)).refresh()
        verify(githubService, times(0)).refresh()
    }

    @Test
    @DisplayName("refreshCertificates mal, traga la excepción y no la propaga")
    fun refreshCertificatesWithError() = runTest {
        whenever(certificatesService.refresh()).thenThrow(RuntimeException("fallo certificados"))

        assertDoesNotThrow {
            service.refreshCertificates()
        }

        verify(certificatesService, times(1)).refresh()
        verify(githubService, times(0)).refresh()
    }

    @Test
    @DisplayName("refreshProjects bien, delega en githubService")
    fun refreshProjects() = runTest {
        service.refreshProjects()

        verify(githubService, times(1)).refresh()
        verify(certificatesService, times(0)).refresh()
    }

    @Test
    @DisplayName("refreshProjects mal, traga la excepción y no la propaga")
    fun refreshProjectsWithError() = runTest {
        whenever(githubService.refresh()).thenThrow(RuntimeException("fallo proyectos"))

        assertDoesNotThrow {
            service.refreshProjects()
        }

        verify(githubService, times(1)).refresh()
        verify(certificatesService, times(0)).refresh()
    }
}
