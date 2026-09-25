package dev.aragorn.portafolioapi.common.schedulers

import dev.aragorn.portafolioapi.PortafolioApiApplication
import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.scheduling.annotation.EnableScheduling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
class PortfolioSchedulerTest {
    @Mock
    private lateinit var refreshService: PortfolioRefreshService

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private lateinit var scheduler: PortfolioScheduler

    @BeforeEach
    fun setup() {
        scheduler = PortfolioScheduler(refreshService, scope)
    }

    @Test
    @DisplayName("la planificación está activada, si no los @Scheduled nunca se ejecutan")
    fun schedulingEnabled() {
        assertTrue(PortafolioApiApplication::class.java.isAnnotationPresent(EnableScheduling::class.java))
    }

    @Test
    @DisplayName("refreshCertificates bien, delega en refreshService")
    fun refreshCertificates() = runTest {
        scheduler.refreshCertificates()

        verify(refreshService, times(1)).refreshCertificates()
        verify(refreshService, times(0)).refreshProjects()
    }

    @Test
    @DisplayName("refreshProjects bien, delega en refreshService")
    fun refreshProjects() = runTest {
        scheduler.refreshProjects()

        verify(refreshService, times(1)).refreshProjects()
        verify(refreshService, times(0)).refreshCertificates()
    }
}
