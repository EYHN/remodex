package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContextWindowUsage(
    val tokensUsed: Long = 0,
    val tokenLimit: Long = 0
) {
    val tokensRemaining: Long get() = maxOf(0, tokenLimit - tokensUsed)
    val fractionUsed: Float get() = if (tokenLimit > 0) tokensUsed.toFloat() / tokenLimit else 0f
    val percentUsed: Int get() = (fractionUsed * 100).toInt()
    val percentRemaining: Int get() = 100 - percentUsed
}

@Serializable
data class CodexRateLimitBucket(
    @SerialName("limit_id") val limitId: String? = null,
    @SerialName("limit_name") val limitName: String? = null,
    val primary: CodexRateLimitWindow? = null,
    val secondary: CodexRateLimitWindow? = null
) {
    val displayRows: List<CodexRateLimitDisplayRow> get() {
        val rows = mutableListOf<CodexRateLimitDisplayRow>()
        primary?.let {
            rows.add(CodexRateLimitDisplayRow(
                id = "${limitId}_primary",
                label = limitName ?: limitId ?: "Limit",
                window = it
            ))
        }
        secondary?.let {
            rows.add(CodexRateLimitDisplayRow(
                id = "${limitId}_secondary",
                label = "${limitName ?: limitId ?: "Limit"} (extended)",
                window = it
            ))
        }
        return rows.sortedBy { it.window.windowDurationMins }
    }
}

@Serializable
data class CodexRateLimitWindow(
    @SerialName("used_percent") val usedPercent: Float = 0f,
    @SerialName("window_duration_mins") val windowDurationMins: Int = 0,
    @SerialName("resets_at") val resetsAt: String? = null
)

data class CodexRateLimitDisplayRow(
    val id: String,
    val label: String,
    val window: CodexRateLimitWindow
)
