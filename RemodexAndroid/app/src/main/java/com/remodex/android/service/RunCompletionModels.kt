package com.remodex.android.service

data class CodexMissingNotificationThreadPrompt(
    val threadId: String
)

enum class CodexRunCompletionResult {
    COMPLETED,
    FAILED
}
