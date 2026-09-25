package dev.aragorn.portafolioapi.certificates.service

import dev.aragorn.portafolioapi.certificates.client.CertificateClient
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.mapper.CertificateMapper
import dev.aragorn.portafolioapi.certificates.repository.CertificatesRepository
import dev.aragorn.portafolioapi.common.service.validator.Validator
import jakarta.transaction.Transactional
import kotlinx.coroutines.withTimeout
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.logging.Logger

/**
 * Implementación de [CertificateService].
 *
 * @param client cliente del servicio externo de certificados.
 * @param certificatesRepository repositorio de certificados.
 * @param mapper conversor DTO ↔ entidad.
 * @param validator validador de cada certificado recibido.
 */
@Service
class CertificateServiceImpl(
    private val client: CertificateClient,
    private val certificatesRepository: CertificatesRepository,
    private val mapper: CertificateMapper,
    private val validator: Validator<CertificatesResponseDto>,
): CertificateService {
    private val log: Logger = Logger.getLogger(CertificateServiceImpl::class.java.name)

    /**
     * Sincroniza la tabla de certificados con lo que publica el servicio externo.
     *
     * 1. Descarga la lista con un límite de 60 s, que cubre todos los reintentos del cliente.
     * 2. Valida cada certificado. Si uno falla, se aborta todo el refresco.
     * 3. Borra los certificados guardados cuyo id ya no aparece en la lista.
     * 4. Inserta o actualiza el resto.
     * 5. Vacía la caché `certificados`.
     *
     * @throws dev.aragorn.portafolioapi.certificates.exceptions.CertificatesExceptions si los datos de origen no son válidos.
     * @throws kotlinx.coroutines.TimeoutCancellationException si la descarga pasa de 60 s.
     */
    @Transactional
    @CacheEvict(cacheNames = ["certificados"], allEntries = true)
    override suspend fun refresh() {
        log.info("Refreshing certificates")
        val newCertificate = withTimeout(60_000) {
            client.findCertificates()
        }
        newCertificate.forEach {
            log.info("validating strings")
            validator.validate(it)
        }
        val certificadoEntity=newCertificate.map(mapper::dtoToModel)
        val oldCertificatesIds = certificatesRepository.findAll().map{it.id}.toSet()
        val newCertificatesIds = certificadoEntity.map{it.id}.toSet()
        val deletedIds = oldCertificatesIds - newCertificatesIds
        if(deletedIds.isNotEmpty()) deletedIds.forEach {
            log.info("deleting certificate $it")
            certificatesRepository.deleteById(it)
        }
        log.info("saving/updating certificates with ids: $deletedIds")
        certificatesRepository.saveAll(certificadoEntity)
        log.info("Certificates synchronized. old: $oldCertificatesIds, new: $newCertificatesIds, deleted: $deletedIds")
    }
    /** Lee todos los certificados de la base de datos y los guarda en la caché `certificados`. */
    @Cacheable(cacheNames = ["certificados"])
    override suspend fun getAll(): List<CertificatesResponseDto> {
        log.info("getting certificates")
        return certificatesRepository.findAll().map { mapper.certificateToDto(it) }.toList()
    }
}