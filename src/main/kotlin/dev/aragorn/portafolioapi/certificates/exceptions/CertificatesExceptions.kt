package dev.aragorn.portafolioapi.certificates.exceptions

/**
 * Raíz de los errores de dominio del módulo de certificados.
 *
 * Hereda de [IllegalArgumentException] porque indica datos de origen no válidos, no un fallo
 * interno de la API.
 */
abstract class CertificatesExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}

/**
 * El servicio de certificados no ha devuelto datos que se puedan usar: cuerpo vacío tras los
 * reintentos, lista vacía o endpoint inexistente (404).
 *
 * Una lista vacía se trata como error a propósito: así un fallo en origen no borra todos los
 * certificados guardados.
 */
class CertificationEmptyException(message: String) : CertificatesExceptions(message)

/** Un certificado no cumple las restricciones del DTO o tiene una fecha que no se puede leer. */
class InvalidCertificateException(message: String) : CertificatesExceptions(message)