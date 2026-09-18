package dev.aragorn.portafolioapi.projects.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubTreeResponse(
    val sha: String? = null,
    val truncated: Boolean = false,
    val tree: List<GithubTreeNode> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubTreeNode(
    val path: String = "",
    val type: String = "",
)
