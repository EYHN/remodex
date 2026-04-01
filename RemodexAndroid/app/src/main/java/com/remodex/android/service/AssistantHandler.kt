package com.remodex.android.service

import com.remodex.android.data.model.*

class AssistantHandler {

    // Track streaming messages by turnId+itemId
    private val streamingMessages = mutableMapOf<String, CodexMessage>()
    private val completionFingerprints = mutableSetOf<String>()

    fun appendAgentDelta(
        threadId: String,
        turnId: String?,
        itemId: String?,
        deltaText: String,
        existingMessages: MutableList<CodexMessage>
    ): Boolean {
        val key = "${turnId ?: ""}:${itemId ?: ""}"
        val existing = streamingMessages[key]

        if (existing != null) {
            // Append delta to existing streaming message
            existing.text += deltaText
            existing.isStreaming = true
            // Update in the list
            val idx = existingMessages.indexOfFirst { it.id == existing.id }
            if (idx >= 0) {
                existingMessages[idx] = existing.copy(text = existing.text, isStreaming = true)
            }
            return true
        }

        // Create new streaming message
        val msg = CodexMessage(
            threadId = threadId,
            role = CodexMessageRole.ASSISTANT,
            kind = CodexMessageKind.CHAT,
            text = deltaText,
            turnId = turnId,
            itemId = itemId,
            isStreaming = true,
            orderIndex = CodexMessageOrderCounter.next(),
            deliveryState = CodexMessageDeliveryState.CONFIRMED
        )
        streamingMessages[key] = msg
        existingMessages.add(msg)
        return true
    }

    fun completeAgentMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        finalText: String?,
        existingMessages: MutableList<CodexMessage>
    ): CodexMessage? {
        val key = "${turnId ?: ""}:${itemId ?: ""}"
        val fp = "$threadId:$key:${finalText?.take(50)}"
        if (fp in completionFingerprints) return null
        completionFingerprints.add(fp)

        val existing = streamingMessages.remove(key)
        if (existing != null) {
            existing.text = finalText ?: existing.text
            existing.isStreaming = false
            val idx = existingMessages.indexOfFirst { it.id == existing.id }
            if (idx >= 0) {
                existingMessages[idx] = existing.copy(
                    text = existing.text,
                    isStreaming = false
                )
            }
            return existingMessages.getOrNull(idx)
        }

        // No streaming message found, create a completed one
        if (!finalText.isNullOrBlank()) {
            val message = CodexMessage(
                threadId = threadId,
                role = CodexMessageRole.ASSISTANT,
                kind = CodexMessageKind.CHAT,
                text = finalText,
                turnId = turnId,
                itemId = itemId,
                isStreaming = false,
                orderIndex = CodexMessageOrderCounter.next(),
                deliveryState = CodexMessageDeliveryState.CONFIRMED
            )
            existingMessages.add(message)
            return message
        }
        return null
    }

    fun appendSystemMessage(
        threadId: String,
        turnId: String?,
        itemId: String?,
        kind: CodexMessageKind,
        text: String,
        existingMessages: MutableList<CodexMessage>,
        commandDetails: CommandExecutionDetails? = null
    ) {
        existingMessages.add(
            CodexMessage(
                threadId = threadId,
                role = CodexMessageRole.SYSTEM,
                kind = kind,
                text = text,
                turnId = turnId,
                itemId = itemId,
                orderIndex = CodexMessageOrderCounter.next(),
                deliveryState = CodexMessageDeliveryState.CONFIRMED,
                commandDetails = commandDetails
            )
        )
    }

    fun clearStreamingState() {
        streamingMessages.clear()
    }

    fun clearCompletionFingerprints() {
        completionFingerprints.clear()
    }
}
