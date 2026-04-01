package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class CodexCollaborationModeKind {
    DEFAULT,
    PLAN;

    val wireValue: String
        get() = name.lowercase()
}

@Serializable
data class CodexPlanState(
    val explanation: String? = null,
    val steps: List<CodexPlanStep> = emptyList()
)

@Serializable
data class CodexPlanStep(
    val id: String = UUID.randomUUID().toString(),
    val step: String,
    val status: CodexPlanStepStatus = CodexPlanStepStatus.PENDING
)

@Serializable
enum class CodexPlanStepStatus {
    @SerialName("pending")
    PENDING,
    @SerialName("in_progress")
    IN_PROGRESS,
    @SerialName("completed")
    COMPLETED
}

@Serializable
data class CodexStructuredUserInputRequest(
    val requestID: JsonValue,
    val questions: List<CodexStructuredUserInputQuestion> = emptyList()
) {
    val requestIdKey: String
        get() = when (requestID) {
            is JsonValue.StringValue -> requestID.value
            is JsonValue.IntValue -> requestID.value.toString()
            else -> requestID.toJsonElement().toString()
        }
}

@Serializable
data class CodexStructuredUserInputQuestion(
    val id: String,
    val header: String? = null,
    val question: String,
    val isOther: Boolean = false,
    val isSecret: Boolean = false,
    val options: List<CodexStructuredUserInputOption> = emptyList()
)

@Serializable
data class CodexStructuredUserInputOption(
    val id: String,
    val label: String,
    val description: String? = null
)

@Serializable
data class CodexSubagentAction(
    val tool: String? = null,
    val status: String? = null,
    val prompt: String? = null,
    val model: String? = null,
    val receiverThreadIds: List<String> = emptyList(),
    val receiverAgents: List<CodexSubagentRef> = emptyList(),
    val agentStates: List<CodexSubagentState> = emptyList()
) {
    val normalizedTool: String get() = tool?.lowercase() ?: ""
    val normalizedStatus: String get() = status?.lowercase() ?: ""

    val summaryText: String get() = buildString {
        if (normalizedTool.isNotEmpty()) append(normalizedTool)
        if (normalizedStatus.isNotEmpty()) {
            if (isNotEmpty()) append(": ")
            append(normalizedStatus)
        }
    }

    fun agentRows(): List<CodexSubagentThreadPresentation> {
        val orderedThreadIds = buildList {
            receiverThreadIds.forEach { threadId ->
                if (!threadId.isNullOrBlank() && !contains(threadId)) {
                    add(threadId)
                }
            }
            receiverAgents.mapNotNullTo(this) { agent ->
                agent.threadId?.takeIf { it.isNotBlank() && !contains(it) }
            }
        }

        return orderedThreadIds.mapIndexed { i, threadId ->
            val agent = receiverAgents.firstOrNull { it.threadId == threadId }
            val state = agentStates.getOrNull(i)
            CodexSubagentThreadPresentation(
                threadId = threadId,
                agentId = agent?.agentId,
                nickname = agent?.nickname,
                role = agent?.role,
                model = agent?.model,
                prompt = prompt,
                fallbackStatus = state?.status,
                fallbackMessage = state?.message
            )
        }
    }
}

@Serializable
data class CodexSubagentRef(
    val threadId: String? = null,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null
)

@Serializable
data class CodexSubagentState(
    val agentId: String? = null,
    val status: String? = null,
    val message: String? = null
)

@Serializable
data class CodexSubagentThreadPresentation(
    val threadId: String? = null,
    val agentId: String? = null,
    val nickname: String? = null,
    val role: String? = null,
    val model: String? = null,
    val modelIsRequestedHint: Boolean = false,
    val prompt: String? = null,
    val fallbackStatus: String? = null,
    val fallbackMessage: String? = null
) {
    val displayLabel: String get() = nickname ?: role ?: agentId ?: "Agent"
}
