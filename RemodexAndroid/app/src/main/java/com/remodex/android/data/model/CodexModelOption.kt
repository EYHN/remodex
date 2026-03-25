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
    val displayLabel: String get() = reasoningEffort.replaceFirstChar { it.uppercase() }
}

@Serializable
enum class CodexServiceTier {
    @SerialName("fast") FAST
}

@Serializable
enum class CodexAccessMode {
    @SerialName("on_request") ON_REQUEST,
    @SerialName("full_access") FULL_ACCESS;

    val displayLabel: String get() = when (this) {
        ON_REQUEST -> "On Request"
        FULL_ACCESS -> "Full Access"
    }

    val approvalPolicy: String get() = when (this) {
        ON_REQUEST -> "unless-allow-listed"
        FULL_ACCESS -> "auto-approve"
    }
}
