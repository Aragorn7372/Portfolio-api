package dev.aragorn.portafolioapi.certificates.service

import dev.aragorn.portafolioapi.certificates.client.CertificateClient
import dev.aragorn.portafolioapi.certificates.client.CertificatesClientImpl
import dev.aragorn.portafolioapi.certificates.dto.CertificatesResponseDto
import dev.aragorn.portafolioapi.certificates.mapper.CertificateMapper
import dev.aragorn.portafolioapi.certificates.model.Certificates
import dev.aragorn.portafolioapi.certificates.repository.CertificatesRepository
import dev.aragorn.portafolioapi.common.service.validator.Validator
import jakarta.transaction.Transactional
import kotlinx.coroutines.withTimeout
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.util.logging.Logger

@Service
class CertificateServiceImpl(
    private val client: CertificateClient,
    private val certificatesRepository: CertificatesRepository,
    private val mapper: CertificateMapper,
    private val validator: Validator<CertificatesResponseDto>,
): CertificateService {
    private val log: Logger = Logger.getLogger(CertificatesClientImpl::class.java.name)
    @Transactional
    @CacheEvict(cacheNames = ["certificados"], allEntries = true)
    override suspend fun refresh() {
        log.info("Refreshing certificates")
        val newCertificate = withTimeout(10_000){
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
    @Cacheable(cacheNames = ["certificados"])
    override suspend fun getAll(): List<Certificates> {
        log.info("getting certificates")
        return certificatesRepository.findAll()
    }
}