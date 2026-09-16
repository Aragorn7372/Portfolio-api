package dev.aragorn.portafolioapi.certificates.repository

import dev.aragorn.portafolioapi.certificates.model.Certificates
import org.springframework.stereotype.Repository
import org.springframework.data.jpa.repository.JpaRepository

@Repository
interface CertificatesRepository : JpaRepository<Certificates, String>