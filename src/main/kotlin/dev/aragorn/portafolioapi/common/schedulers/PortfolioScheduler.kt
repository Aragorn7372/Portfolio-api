package dev.aragorn.portafolioapi.common.schedulers

import kotlinx.coroutines.CoroutineScope
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PortfolioScheduler(
    private val portfolioScope: CoroutineScope,
    private val refreshService: PortfolioRefreshService
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