package dev.aragorn.portafolioapi.common.startup

import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.ApplicationArguments
import org.springframework.stereotype.Component

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MockitoExtension::class)
class PortfolioStartupRefreshTest {
    @Mock
    private lateinit var refreshService: PortfolioRefreshService

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private lateinit var runner: PortfolioStartupRefresh

    @BeforeEach
    fun setup() {
        runner = PortfolioStartupRefresh(refreshService, scope)
    }

    @Test
    @DisplayName("es un bean de Spring, si no el refresco inicial nunca se lanza")
    fun isComponent() {
        assertTrue(PortfolioStartupRefresh::class.java.isAnnotationPresent(Component::class.java))
    }

    @Test
    @DisplayName("run bien, lanza refresh de projects y certificates")
    fun run() = runTest {
        val args: ApplicationArguments = mock()

        runner.run(args)

        verify(refreshService, times(1)).refreshProjects()
        verify(refreshService, times(1)).refreshCertificates()
    }
}
