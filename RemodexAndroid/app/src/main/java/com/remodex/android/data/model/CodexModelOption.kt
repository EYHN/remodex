package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CodexModelOption(
    val id: String,
    val model: String,
    @SerialName("display_name") val displayName: String? = null,
    val description: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("supported_reasoning_efforts")
    val supportedReasoningEfforts: List<String> = emptyList(),
    @SerialName("default_reasoning_effort")
    val defaultReasoningEffort: String? = null
) {
    val label: String get() = displayName ?: model
}

@Serializable
data class CodexReasoningEffortOption(
    val reasoningEffort: String,
    val description: String? = null
) {
    val displayLabel: String get() = reasoningEffort.reasoningEffortDisplayLabel()
}

@Serializable
enum class CodexServiceTier {
    @SerialName("fast") FAST;

    val displayLabel: String
        get() = when (this) {
            FAST -> "Fast"
        }
}

@Serializable
enum class CodexAccessMode {
    @SerialName("on_request") ON_REQUEST,
    @SerialName("full_access") FULL_ACCESS;

    val displayLabel: String get() = when (this) {
        ON_REQUEST -> "On Request"
        FULL_ACCESS -> "Full Access"
    }

    val approvalPolicyCandidates: List<String> get() = when (this) {
        ON_REQUEST -> listOf("on-request", "onRequest")
        FULL_ACCESS -> listOf("never")
    }

    val sandboxLegacyValue: String get() = when (this) {
        ON_REQUEST -> "workspace-write"
        FULL_ACCESS -> "danger-full-access"
    }
}

fun String.reasoningEffortDisplayLabel(): String {
    return trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
}
