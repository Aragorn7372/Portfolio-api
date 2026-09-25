package dev.aragorn.portafolioapi.common.service.validator

/**
 * Contrato común de los validadores de datos que llegan de fuentes externas.
 *
 * Se validan los DTOs de GitHub y del servicio de certificados antes de persistirlos, para que un
 * dato mal formado en origen no llegue a la base de datos ni al cliente.
 *
 * @param T tipo del valor que se valida.
 * @see dev.aragorn.portafolioapi.projects.validator.GithubRepositoryValidator
 * @see dev.aragorn.portafolioapi.certificates.validator.CertificateValidator
 */
interface Validator<T> {
    /**
     * Valida el valor y lanza una excepción si no es correcto.
     *
     * @param value valor que se valida.
     * @throws IllegalArgumentException (o una subclase del módulo) si el valor no cumple las restricciones.
     */
    fun validate(value: T)
}