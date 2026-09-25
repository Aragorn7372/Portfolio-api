package dev.aragorn.portafolioapi.projects.exceptions

/**
 * Raíz de los errores de validación de los datos de repositorios.
 *
 * A diferencia de [dev.aragorn.portafolioapi.projects.client.GithubException], que indica un fallo
 * de comunicación, esta familia indica que GitHub respondió bien pero los datos no son válidos.
 */
abstract class GithubRepositoryExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}

/**
 * Un repositorio no ha pasado la validación de
 * [dev.aragorn.portafolioapi.projects.validator.GithubRepositoryValidator]. El mensaje junta
 * todas las restricciones incumplidas.
 */
class InvalidGithubRepositoryException(message: String) : GithubRepositoryExceptions(message)
