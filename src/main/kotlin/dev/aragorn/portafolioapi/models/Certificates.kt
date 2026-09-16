package dev.aragorn.portafolioapi.models

import java.net.URL
import java.time.LocalDate

data class Certificates(
    private val name: String,
    private val url: URL,
    private val date: LocalDate
)
