package dev.aragorn.portafolioapi.projects.exceptions

abstract class GithubRepositoryExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}

class InvalidGithubRepositoryException(message: String) : GithubRepositoryExceptions(message)
