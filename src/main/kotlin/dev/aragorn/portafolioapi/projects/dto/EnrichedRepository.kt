package dev.aragorn.portafolioapi.projects.dto

import kotlin.math.round


data class EnrichedRepository(
    val repository: GithubRepositoryResponse,
    val languages: Map<String, Double>,
    val commits: Int,
    val pagesUrl: String?,
)

fun Map<String, Long>.toPercentages(): Map<String, Double> {
    val total = values.sum().toDouble()
    if (total <= 0) return emptyMap()
    return mapValues { (_, bytes) -> round(bytes * 1000.0 / total) / 10.0 }
}
