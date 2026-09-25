package dev.aragorn.portafolioapi.common.schedulers

import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Lanza periódicamente el refresco de certificados y de proyectos.
 *
 * Las frecuencias se configuran en horas y se pasan a milisegundos con SpEL:
 * - `app.reniew.certs.time`: cada cuántas horas se refrescan los certificados.
 * - `app.reniew.projects.time`: cada cuántas horas se refrescan los proyectos.
 *
 * Cada ejecución lanza una corrutina en el [CoroutineScope] compartido
 * ([dev.aragorn.portafolioapi.common.config.CoroutineConfig]) y termina enseguida, así no bloquea
 * el hilo del planificador de Spring aunque el refresco tarde.
 *
 * Nota: `@Scheduled` solo se ejecuta si la planificación está activada con `@EnableScheduling`.
 *
 * @param refreshService servicio que hace el refresco.
 * @param scope scope en el que se lanzan las corrutinas.
 */
@Component
class PortfolioScheduler(
    private val refreshService: PortfolioRefreshService,
    private val scope: CoroutineScope
) {
    /** Lanza en segundo plano [PortfolioRefreshService.refreshCertificates] cada `app.reniew.certs.time` horas. */
    @Scheduled(fixedRateString = $$"#{${app.reniew.certs.time} * 3600000}")
    fun refreshCertificates() {
        scope.launch {
            refreshService.refreshCertificates()
        }
    }

    /** Lanza en segundo plano [PortfolioRefreshService.refreshProjects] cada `app.reniew.projects.time` horas. */
    @Scheduled(fixedRateString = $$"#{${app.reniew.projects.time} * 3600000}")
    fun refreshProjects() {
        scope.launch {
            refreshService.refreshProjects()
        }
    }

}