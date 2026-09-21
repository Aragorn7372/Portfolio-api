package dev.aragorn.portafolioapi.common.startup

import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.util.logging.Logger


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
