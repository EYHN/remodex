package com.remodex.android.service

import com.remodex.android.data.model.*
import kotlinx.serialization.json.*

class HistoryDecoder(private val json: Json) {

    fun decodeMessagesFromThreadRead(threadId: String, params: JsonValue?): List<CodexMessage> {
        val obj = params?.objectValue ?: return emptyList()
        val turns = obj["turns"]?.arrayValue ?: return emptyList()
        val messages = mutableListOf<CodexMessage>()

        for (turn in turns) {
            val turnObj = turn.objectValue ?: continue
            val turnId = turnObj["id"]?.stringValue ?: turnObj["turnId"]?.stringValue
            val turnTimestamp = turnObj["created_at"]?.stringValue
                ?: turnObj["createdAt"]?.stringValue
            val items = turnObj["items"]?.arrayValue ?: continue

            for ((itemIndex, item) in items.withIndex()) {
                val itemObj = item.objectValue ?: continue
                val decoded = decodeItem(threadId, turnId, itemObj, turnTimestamp, itemIndex)
                if (decoded != null) {
                    messages.add(decoded)
                }
            }
        }

        // Sort by orderIndex
        return messages.sortedBy { it.orderIndex }
    }

    private fun decodeItem(
        threadId: String,
        turnId: String?,
        item: Map<String, JsonValue>,
        turnTimestamp: String?,
        offsetIndex: Int
    ): CodexMessage? {
        val itemId = item["id"]?.stringValue ?: item["itemId"]?.stringValue
        val itemType = item["type"]?.stringValue
            ?: item["item_type"]?.stringValue
            ?: item["kind"]?.stringValue
            ?: ""
        val itemTimestamp = item["created_at"]?.stringValue
            ?: item["createdAt"]?.stringValue
            ?: turnTimestamp

        val (role, kind) = when (itemType.lowercase()) {
            "usermessage", "user_message" -> CodexMessageRole.USER to CodexMessageKind.CHAT
            "agentmessage", "agent_message", "assistantmessage", "assistant_message", "message" ->
                CodexMessageRole.ASSISTANT to CodexMessageKind.CHAT
            "reasoning" -> CodexMessageRole.SYSTEM to CodexMessageKind.THINKING
            "filechange", "file_change", "diff" -> CodexMessageRole.SYSTEM to CodexMessageKind.FILE_CHANGE
            "toolcall", "tool_call" -> CodexMessageRole.SYSTEM to CodexMessageKind.TOOL_ACTIVITY
            "commandexecution", "command_execution" -> CodexMessageRole.SYSTEM to CodexMessageKind.COMMAND_EXECUTION
            "subagentaction", "subagent_action", "collabtoolcall", "collab_tool_call" ->
                CodexMessageRole.SYSTEM to CodexMessageKind.SUBAGENT_ACTION
            "plan" -> CodexMessageRole.SYSTEM to CodexMessageKind.PLAN
            "enteredreviewmode", "entered_review_mode" ->
                CodexMessageRole.SYSTEM to CodexMessageKind.CHAT
            "exitedreviewmode", "exited_review_mode" ->
                CodexMessageRole.SYSTEM to CodexMessageKind.CHAT
            else -> CodexMessageRole.SYSTEM to CodexMessageKind.CHAT
        }

        val text = decodeItemText(item, itemType)
        if (text.isBlank() && kind == CodexMessageKind.CHAT && role != CodexMessageRole.USER) {
            return null // Skip empty assistant messages
        }

        val attachments = decodeImageAttachments(item)
        val commandDetails = if (kind == CodexMessageKind.COMMAND_EXECUTION) {
            decodeCommandDetails(item)
        } else null

        return CodexMessage(
            threadId = threadId,
            role = role,
            kind = kind,
            text = text,
            createdAt = CodexThread.parseTimestamp(itemTimestamp),
            turnId = turnId,
            itemId = itemId,
            orderIndex = CodexMessageOrderCounter.next(),
            attachments = attachments,
            commandDetails = commandDetails,
            deliveryState = CodexMessageDeliveryState.CONFIRMED
        )
    }

    private fun decodeItemText(item: Map<String, JsonValue>, itemType: String): String {
        // Try multiple field names
        val candidates = listOf("text", "content", "message", "output", "summary", "description")
        for (field in candidates) {
            item[field]?.stringValue?.let { if (it.isNotBlank()) return it }
        }

        // Reasoning-specific
        if (itemType.lowercase() == "reasoning") {
            item["reasoning"]?.stringValue?.let { return it }
            item["thinking"]?.stringValue?.let { return it }
        }

        // File change summary
        if (itemType.lowercase() in listOf("filechange", "file_change", "diff")) {
            return decodeFileChangeText(item)
        }

        // Command execution
        if (itemType.lowercase() in listOf("commandexecution", "command_execution")) {
            return decodeCommandExecutionText(item)
        }

        return ""
    }

    private fun decodeFileChangeText(item: Map<String, JsonValue>): String {
        val path = item["path"]?.stringValue ?: item["filePath"]?.stringValue ?: ""
        val action = item["action"]?.stringValue ?: item["changeType"]?.stringValue ?: "modified"
        val additions = item["additions"]?.intValue ?: 0
        val deletions = item["deletions"]?.intValue ?: 0
        return buildString {
            append("$action: $path")
            if (additions > 0 || deletions > 0) append(" (+$additions/-$deletions)")
        }
    }

    private fun decodeCommandExecutionText(item: Map<String, JsonValue>): String {
        val command = item["command"]?.stringValue
            ?: item["fullCommand"]?.stringValue
            ?: item["call"]?.stringValue ?: ""
        val output = item["output"]?.stringValue ?: item["outputTail"]?.stringValue ?: ""
        return buildString {
            if (command.isNotBlank()) append("$ $command")
            if (output.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(output)
            }
        }
    }

    private fun decodeCommandDetails(item: Map<String, JsonValue>): CommandExecutionDetails {
        return CommandExecutionDetails(
            fullCommand = item["command"]?.stringValue
                ?: item["fullCommand"]?.stringValue
                ?: item["call"]?.stringValue ?: "",
            cwd = item["cwd"]?.stringValue,
            exitCode = item["exitCode"]?.intValue?.toInt() ?: item["exit_code"]?.intValue?.toInt(),
            durationMs = item["durationMs"]?.intValue ?: item["duration_ms"]?.intValue,
            outputTail = item["output"]?.stringValue ?: item["outputTail"]?.stringValue ?: ""
        )
    }

    private fun decodeImageAttachments(item: Map<String, JsonValue>): List<CodexImageAttachment> {
        val images = item["images"]?.arrayValue ?: return emptyList()
        return images.mapNotNull { img ->
            val imgObj = img.objectValue ?: return@mapNotNull null
            CodexImageAttachment(
                thumbnailBase64JPEG = imgObj["thumbnail"]?.stringValue
                    ?: imgObj["thumbnailBase64JPEG"]?.stringValue,
                payloadDataURL = imgObj["url"]?.stringValue
                    ?: imgObj["payloadDataURL"]?.stringValue
            )
        }
    }
}
