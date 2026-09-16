package dev.aragorn.portafolioapi.certificates.Exceptions

abstract class CertificatesExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}
class CertificationEmptyException(message: String) : CertificatesExceptions(message)
class InvalidCertificateException(message: String) : CertificatesExceptions(message)