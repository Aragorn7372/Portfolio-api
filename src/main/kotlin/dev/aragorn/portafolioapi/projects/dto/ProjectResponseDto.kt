package dev.aragorn.portafolioapi.projects.dto


data class ProjectResponseDto(
    val name: String,
    val description: String?,
    val url: String,
    val pagesUrl: String?,
    val owner: String,
    val avatarUrl: String,
    val stars: Int,
    val forks: Int,
    val commits: Int,
    val languages: Map<String, Double>,
    val topics: List<String>,
    val technologies: List<String> = emptyList(),
)
