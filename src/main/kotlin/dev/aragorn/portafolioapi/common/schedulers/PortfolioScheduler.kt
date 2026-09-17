package dev.aragorn.portafolioapi.common.schedulers

import dev.aragorn.portafolioapi.common.service.porfolio.PortfolioRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PortfolioScheduler(
    private val refreshService: PortfolioRefreshService,
    private val scope: CoroutineScope
) {
    @Scheduled(fixedRateString = $$"#{${app.reniew.certs.time} * 3600000}")
    fun refreshCertificates() {
        scope.launch {
            refreshService.refreshCertificates()
        }
    }
    @Scheduled(fixedRateString = $$"#{${app.reniew.projects.time} * 3600000}")
    fun refreshProjects() {
        scope.launch {
            refreshService.refreshProjects()
        }
    }

}