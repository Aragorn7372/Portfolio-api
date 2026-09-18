package dev.aragorn.portafolioapi.common.startup

import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.logging.Logger

/**
 * Refresh inicial al arrancar: puebla PostgreSQL (y de paso las caches en la
 * primera lectura) para no servir vacío hasta el primer tick del scheduler.
 * No bloquea el arranque y nunca lo tumba: PortfolioRefreshImpl ya captura
 * los errores de GitHub con un warning.
 * Desactivable con APP_REFRESH_ON_STARTUP=false.
 */
@Component
@ConditionalOnProperty(name = ["app.refresh-on-startup"], havingValue = "true", matchIfMissing = true)
class PortfolioStartupRefresh(
    private val refreshService: PortfolioRefreshService,
    private val scope: CoroutineScope,
) : ApplicationRunner {

    private val log: Logger = Logger.getLogger(PortfolioStartupRefresh::class.java.name)

    override fun run(args: ApplicationArguments) {
        log.info("Lanzando refresh inicial de portfolio")
        scope.launch { refreshService.refreshProjects() }
        scope.launch { refreshService.refreshCertificates() }
    }
}
