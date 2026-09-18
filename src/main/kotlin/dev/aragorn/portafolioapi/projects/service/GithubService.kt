package dev.aragorn.portafolioapi.projects.service

import dev.aragorn.portafolioapi.projects.dto.ProjectResponseDto

interface GithubService {
    suspend fun refresh()
    suspend fun getProjects(): List<ProjectResponseDto>
}
