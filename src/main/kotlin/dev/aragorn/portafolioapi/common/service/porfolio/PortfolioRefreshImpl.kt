package dev.aragorn.portafolioapi.common.service.porfolio

import dev.aragorn.portafolioapi.certificates.service.CertificateService
import dev.aragorn.portafolioapi.projects.service.GithubService
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
class PortfolioRefreshImpl(
    private val certificatesService: CertificateService,
    private val githubService: GithubService
) : PortfolioRefreshService {
    private val log: Logger = Logger.getLogger(PortfolioRefreshImpl::class.java.name)
    override suspend  fun refreshCertificates() {
        try {
            log.info("Starting certificates refresh")
            certificatesService.refresh()
        }catch (e:Exception){
            log.warning("Error while refreshing certificates:" +e.message)
        }
    }

    override suspend fun refreshProjects() {
        try {
            log.info("Starting project refresh")
            githubService.refresh()
        }catch (e:Exception){
            log.warning("Error while refreshing projects:" +e.message)
        }
    }
}