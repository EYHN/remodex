package com.remodex.android.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CodexMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val role: CodexMessageRole = CodexMessageRole.USER,
    val kind: CodexMessageKind = CodexMessageKind.CHAT,
    var text: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val turnId: String? = null,
    val itemId: String? = null,
    var isStreaming: Boolean = false,
    var deliveryState: CodexMessageDeliveryState = CodexMessageDeliveryState.CONFIRMED,
    var orderIndex: Int = 0,
    var attachments: List<CodexImageAttachment> = emptyList(),
    var planState: CodexPlanState? = null,
    var subagentAction: CodexSubagentAction? = null,
    var structuredUserInputRequest: CodexStructuredUserInputRequest? = null,
    var commandDetails: CommandExecutionDetails? = null
) {
    val isUser: Boolean get() = role == CodexMessageRole.USER
    val isAssistant: Boolean get() = role == CodexMessageRole.ASSISTANT
    val isSystem: Boolean get() = role == CodexMessageRole.SYSTEM
}

@Serializable
enum class CodexMessageRole {
    USER, ASSISTANT, SYSTEM
}

@Serializable
enum class CodexMessageKind {
    CHAT, THINKING, TOOL_ACTIVITY, FILE_CHANGE,
    COMMAND_EXECUTION, SUBAGENT_ACTION, PLAN, USER_INPUT_PROMPT
}

@Serializable
enum class CodexMessageDeliveryState {
    PENDING, CONFIRMED, FAILED
}
