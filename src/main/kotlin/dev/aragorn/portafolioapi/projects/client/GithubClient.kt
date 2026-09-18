package dev.aragorn.portafolioapi.projects.client

import dev.aragorn.portafolioapi.projects.dto.GithubRepositoryResponse

interface GithubClient {

    suspend fun findUserRepositories(
        username: String,
    ): List<GithubRepositoryResponse>

    suspend fun findOrganizationRepositories(
        organization: String,
    ): List<GithubRepositoryResponse>

    suspend fun findLanguages(
        owner: String,
        repository: String,
    ): Map<String, Long>

    suspend fun findCommitCount(
        owner: String,
        repository: String,
    ): Int
    suspend fun findPagesUrl(
        owner: String,
        repository: String,
    ): String?

    suspend fun findRepositoryTree(
        owner: String,
        repository: String,
        ref: String,
    ): List<String>

    suspend fun findFileContent(
        owner: String,
        repository: String,
        path: String,
        ref: String,
    ): String?
}
