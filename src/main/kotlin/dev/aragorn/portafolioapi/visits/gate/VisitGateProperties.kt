package dev.aragorn.portafolioapi.visits.gate

import org.springframework.boot.context.properties.ConfigurationProperties

data class GateRule(
    val pattern: String = "",
    val auth: Boolean = false,
    val fpPerMinute: Int = 120,
    val ipPerMinute: Int = 30,
)


@ConfigurationProperties(prefix = "app.gate")
data class VisitGateProperties(
    val rules: List<GateRule> = emptyList(),
)
