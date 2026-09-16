package dev.aragorn.portafolioapi.common.service.porfolio

interface PortfolioRefreshService {
    suspend fun refreshCertificates()
    suspend fun refreshProjects()
}