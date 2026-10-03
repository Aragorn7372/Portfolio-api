package dev.aragorn.portafolioapi.experience.exceptions

abstract class ExperiencesExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}

class StorageExperienceException(message: String) : ExperiencesExceptions(message)
class ExperienceNotFoundException(message: String) : ExperiencesExceptions(message)
class ExperienceValidationException(message: String) : ExperiencesExceptions(message)