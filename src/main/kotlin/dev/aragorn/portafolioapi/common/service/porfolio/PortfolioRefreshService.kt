package dev.aragorn.portafolioapi.common.service.porfolio

/**
 * Punto único para refrescar los datos del portafolio desde sus fuentes externas.
 *
 * Lo usan tanto el refresco periódico
 * ([dev.aragorn.portafolioapi.common.schedulers.PortfolioScheduler]) como el refresco al
 * arrancar ([dev.aragorn.portafolioapi.common.startup.PortfolioStartupRefresh]). Las
 * implementaciones no deben propagar excepciones: un refresco fallido se registra y los datos
 * que ya había se siguen sirviendo.
 *
 * @see PortfolioRefreshImpl
 */
interface PortfolioRefreshService {
    /** Vuelve a descargar los certificados y sincroniza la base de datos y la caché. */
    suspend fun refreshCertificates()

    /** Vuelve a descargar los proyectos de GitHub y sincroniza la base de datos y la caché. */
    suspend fun refreshProjects()
}