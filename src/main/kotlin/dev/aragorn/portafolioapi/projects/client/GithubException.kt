package dev.aragorn.portafolioapi.projects.client

import java.time.Instant

sealed class GithubException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


class GithubRateLimitExceededException(
    val resetAt: Instant?,
    message: String,
) : GithubException(message)

class GithubUnauthorizedException(message: String) : GithubException(message)

class GithubNotFoundException(message: String) : GithubException(message)

class GithubServerException(message: String, cause: Throwable? = null) : GithubException(message, cause)

class GithubTimeoutException(message: String, cause: Throwable? = null) : GithubException(message, cause)

class GithubInvalidResponseException(message: String) : GithubException(message)
