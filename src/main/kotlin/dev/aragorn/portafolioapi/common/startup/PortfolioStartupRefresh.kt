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
 * Lanza un refresco de proyectos y certificados al arrancar la aplicación.
 *
 * Así una instancia recién desplegada tiene datos sin esperar al primer ciclo de
 * [dev.aragorn.portafolioapi.common.schedulers.PortfolioScheduler]. Los dos refrescos se lanzan
 * en paralelo en el [CoroutineScope] compartido y no bloquean el arranque: la aplicación empieza
 * a aceptar peticiones mientras se descargan los datos.
 *
 * Se controla con `app.refresh-on-startup` (activo si no se define). Conviene ponerlo a `false`
 * en tests y en CI para no depender de servicios externos.
 *
 * @param refreshService servicio que hace el refresco.
 * @param scope scope en el que se lanzan las corrutinas.
 */
@Component
@ConditionalOnProperty(name = ["app.refresh-on-startup"], havingValue = "true", matchIfMissing = true)
class PortfolioStartupRefresh(
    private val refreshService: PortfolioRefreshService,
    private val scope: CoroutineScope,
) : ApplicationRunner {

    private val log: Logger = Logger.getLogger(PortfolioStartupRefresh::class.java.name)

    /**
     * Lanza el refresco inicial en segundo plano y vuelve enseguida.
     *
     * @param args argumentos de arranque (no se usan).
     */
    override fun run(args: ApplicationArguments) {
        log.info("Lanzando refresh inicial de portfolio")
        scope.launch { refreshService.refreshProjects() }
        scope.launch { refreshService.refreshCertificates() }
    }
}
