package dev.aragorn.portafolioapi.experience.dto

import java.io.File

data class StorageImage(
    val path: String,
    val file: File,
    val contentHash: String
)