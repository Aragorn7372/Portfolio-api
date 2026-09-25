package dev.aragorn.portafolioapi.certificates.service

import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto


/**
 * Casos de uso del módulo de certificados.
 *
 * Igual que en proyectos, [refresh] es el único que habla con el servicio externo y [getAll]
 * solo lee de la base de datos, a través de la caché.
 *
 * @see CertificateServiceImpl
 */
interface CertificateService {
    /** Descarga, valida y sincroniza los certificados con la base de datos. */
    suspend fun refresh()

    /**
     * Devuelve los certificados guardados.
     *
     * @return todos los certificados.
     */
    suspend fun getAll(): List<CertificatesResponseDto>
}