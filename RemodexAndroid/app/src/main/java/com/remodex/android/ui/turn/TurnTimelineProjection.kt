package com.remodex.android.ui.turn

import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexMessageKind
import com.remodex.android.data.model.CodexMessageRole
import com.remodex.android.data.model.CodexPlanStepStatus

data class TurnTimelineProjection(
    val messages: List<CodexMessage>,
    val pinnedPlanMessage: CodexMessage?
)

object TurnTimelineProjector {
    fun project(messages: List<CodexMessage>): TurnTimelineProjection {
        val collapsedThinking = collapseThinkingMessages(messages)
        val compactedThinking = removeRedundantThinkingCommandActivityMessages(collapsedThinking)
        val pinnedPlanMessage = compactedThinking.lastOrNull { it.shouldDisplayPinnedPlanAccessory() }
        val visibleMessages = compactedThinking.filterNot { message ->
            pinnedPlanMessage != null &&
                message.id == pinnedPlanMessage.id &&
                message.isPlanSystemMessage()
        }
        return TurnTimelineProjection(
            messages = visibleMessages,
            pinnedPlanMessage = pinnedPlanMessage
        )
    }

    private fun collapseThinkingMessages(messages: List<CodexMessage>): List<CodexMessage> {
        val result = mutableListOf<CodexMessage>()
        messages.forEach { message ->
            if (message.role != CodexMessageRole.SYSTEM || message.kind != CodexMessageKind.THINKING) {
                result += message
                return@forEach
            }

            val previousIndex = latestReusableThinkingIndex(result, message)
            if (previousIndex == null) {
                result += message
                return@forEach
            }

            val previous = result[previousIndex]
            val incomingText = message.text.trim()
            result[previousIndex] = previous.copy(
                text = mergeThinkingText(previous.text, incomingText),
                isStreaming = message.isStreaming,
                turnId = message.turnId ?: previous.turnId,
                itemId = message.itemId ?: previous.itemId
            )
        }
        return result
    }

    private fun latestReusableThinkingIndex(
        messages: List<CodexMessage>,
        incoming: CodexMessage
    ): Int? {
        for (index in messages.indices.reversed()) {
            val candidate = messages[index]
            if (candidate.role == CodexMessageRole.ASSISTANT || candidate.role == CodexMessageRole.USER) {
                break
            }

            if (candidate.role == CodexMessageRole.SYSTEM && candidate.kind == CodexMessageKind.THINKING) {
                if (shouldMergeThinkingRows(candidate, incoming)) {
                    return index
                }
            }
        }
        return null
    }

    private fun shouldMergeThinkingRows(previous: CodexMessage, incoming: CodexMessage): Boolean {
        val previousItemId = normalizedIdentifier(previous.itemId)
        val incomingItemId = normalizedIdentifier(incoming.itemId)
        if (previousItemId != null && incomingItemId != null && previousItemId == incomingItemId) {
            return true
        }

        val previousTurnId = normalizedIdentifier(previous.turnId)
        val incomingTurnId = normalizedIdentifier(incoming.turnId)
        if (previousTurnId != null && incomingTurnId != null && previousTurnId != incomingTurnId) {
            return false
        }

        val previousText = ThinkingDisclosureParser.normalizedThinkingContent(previous.text).trim()
        val incomingText = ThinkingDisclosureParser.normalizedThinkingContent(incoming.text).trim()
        if (previousText.isBlank() || incomingText.isBlank()) {
            return true
        }

        val previousLower = previousText.lowercase()
        val incomingLower = incomingText.lowercase()
        return previousLower == incomingLower ||
            previousLower.contains(incomingLower) ||
            incomingLower.contains(previousLower)
    }

    private fun mergeThinkingText(existing: String, incoming: String): String {
        val existingTrimmed = existing.trim()
        val incomingTrimmed = incoming.trim()
        if (incomingTrimmed.isBlank()) return existingTrimmed
        if (existingTrimmed.isBlank()) return incomingTrimmed

        val existingNormalized = ThinkingDisclosureParser.normalizedThinkingContent(existingTrimmed)
        val incomingNormalized = ThinkingDisclosureParser.normalizedThinkingContent(incomingTrimmed)
        if (incomingNormalized.equals(existingNormalized, ignoreCase = true)) {
            return incomingTrimmed
        }
        if (incomingNormalized.contains(existingNormalized, ignoreCase = true)) {
            return incomingTrimmed
        }
        if (existingNormalized.contains(incomingNormalized, ignoreCase = true)) {
            return existingTrimmed
        }
        return listOf(existingTrimmed, incomingTrimmed).distinct().joinToString(separator = "\n")
    }

    private fun removeRedundantThinkingCommandActivityMessages(
        messages: List<CodexMessage>
    ): List<CodexMessage> {
        val commandKeysByTurn = mutableMapOf<String, MutableSet<String>>()
        messages.forEach { message ->
            if (message.role != CodexMessageRole.SYSTEM || message.kind != CodexMessageKind.COMMAND_EXECUTION) {
                return@forEach
            }
            val turnId = normalizedIdentifier(message.turnId) ?: return@forEach
            val commandKey = commandActivityKey(message.commandDetails?.fullCommand ?: message.text) ?: return@forEach
            commandKeysByTurn.getOrPut(turnId) { mutableSetOf() } += commandKey
        }

        if (commandKeysByTurn.isEmpty()) {
            return messages
        }

        return messages.filter { message ->
            if (message.role != CodexMessageRole.SYSTEM || message.kind != CodexMessageKind.THINKING) {
                return@filter true
            }
            val turnId = normalizedIdentifier(message.turnId) ?: return@filter true
            val commandKeys = commandKeysByTurn[turnId] ?: return@filter true
            val normalizedText = ThinkingDisclosureParser.normalizedThinkingContent(message.text)
            val lines = normalizedText
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (lines.isEmpty()) {
                return@filter true
            }
            !lines.all { line ->
                val commandKey = commandActivityKey(line) ?: return@all false
                commandKeys.contains(commandKey)
            }
        }
    }

    private fun commandActivityKey(text: String): String? {
        val tokens = text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.size < 2) {
            return null
        }

        val status = tokens.first().lowercase()
        if (status !in setOf("running", "completed", "failed", "stopped")) {
            return null
        }
        return tokens.drop(1).joinToString(separator = " ").trim().lowercase().takeIf { it.isNotEmpty() }
    }

    private fun normalizedIdentifier(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }
}

fun CodexMessage.isPlanSystemMessage(): Boolean =
    role == CodexMessageRole.SYSTEM && kind == CodexMessageKind.PLAN

fun CodexMessage.shouldDisplayPinnedPlanAccessory(): Boolean {
    if (!isPlanSystemMessage()) {
        return false
    }
    if (isStreaming) {
        return true
    }

    val steps = planState?.steps.orEmpty()
    if (steps.isEmpty()) {
        return false
    }
    return steps.any { it.status != CodexPlanStepStatus.COMPLETED }
}
