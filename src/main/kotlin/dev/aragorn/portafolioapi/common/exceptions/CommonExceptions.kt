package dev.aragorn.portafolioapi.common.exceptions

abstract class CommonExceptions : IllegalArgumentException {
    protected constructor(message: String) : super(message)
}
class CloudinaryException(message: String) : CommonExceptions(message)