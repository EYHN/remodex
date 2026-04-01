package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CodexFuzzyFileMatch(
    val root: String? = null,
    val path: String = "",
    @SerialName("file_name") val fileNameSnake: String? = null,
    val fileName: String? = null,
    val score: Double = 0.0,
    val indices: List<Int> = emptyList()
) {
    val resolvedFileName: String
        get() = fileName ?: fileNameSnake ?: path.substringAfterLast('/')
}

@Serializable
data class CodexSkillMetadata(
    val name: String,
    val description: String? = null,
    val path: String? = null,
    val scope: String? = null,
    val enabled: Boolean = true
) {
    val normalizedName: String
        get() = name.removePrefix("/").lowercase()
}

@Serializable
data class CodexTurnSkillMention(
    val id: String,
    val name: String? = null,
    val path: String? = null
)
