package dev.aragorn.portafolioapi.common.service.porfolio

import dev.aragorn.portafolioapi.certificates.service.CertificateService
import dev.aragorn.portafolioapi.projects.service.GithubService
import org.springframework.stereotype.Service
import java.util.logging.Logger

/**
 * Implementación de [PortfolioRefreshService] que delega en el servicio de cada módulo.
 *
 * Envuelve cada refresco en un `try/catch`: cualquier error (servicio externo caído, cuota
 * agotada, datos inválidos) se registra como aviso y no se propaga. Así un fallo no mata la
 * corrutina del scheduler y la API sigue sirviendo los datos anteriores desde la base de datos.
 *
 * @param certificatesService servicio de certificados.
 * @param githubService servicio de proyectos.
 */
@Service
class PortfolioRefreshImpl(
    private val certificatesService: CertificateService,
    private val githubService: GithubService
) : PortfolioRefreshService {
    private val log: Logger = Logger.getLogger(PortfolioRefreshImpl::class.java.name)

    /** Llama a [CertificateService.refresh]. Los errores solo se registran en el log. */
    override suspend  fun refreshCertificates() {
        try {
            log.info("Starting certificates refresh")
            certificatesService.refresh()
        }catch (e:Exception){
            log.warning("Error while refreshing certificates:" +e.message)
        }
    }

    /** Llama a [GithubService.refresh]. Los errores solo se registran en el log. */
    override suspend fun refreshProjects() {
        try {
            log.info("Starting project refresh")
            githubService.refresh()
        }catch (e:Exception){
            log.warning("Error while refreshing projects:" +e.message)
        }
    }
}