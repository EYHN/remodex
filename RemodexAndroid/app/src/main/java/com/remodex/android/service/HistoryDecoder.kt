package com.remodex.android.service

import com.remodex.android.data.model.*
import kotlinx.serialization.json.*

class HistoryDecoder(private val json: Json) {

    fun decodeMessagesFromThreadRead(threadId: String, params: JsonValue?): List<CodexMessage> {
        val resultObject = params?.objectValue ?: return emptyList()
        val threadObject = resultObject["thread"]?.objectValue ?: resultObject
        val turns = threadObject["turns"]?.arrayValue ?: return emptyList()
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

        val loweredItemType = itemType.lowercase()
        val decodedToolCall = if (loweredItemType in setOf("toolcall", "tool_call")) {
            decodeToolCallMessage(item)
        } else {
            null
        }
        val (role, kind) = when {
            loweredItemType == "usermessage" || loweredItemType == "user_message" ->
                CodexMessageRole.USER to CodexMessageKind.CHAT
            loweredItemType in setOf("agentmessage", "agent_message", "assistantmessage", "assistant_message", "message") ->
                CodexMessageRole.ASSISTANT to CodexMessageKind.CHAT
            loweredItemType == "reasoning" ->
                CodexMessageRole.SYSTEM to CodexMessageKind.THINKING
            loweredItemType in setOf("filechange", "file_change", "diff") ->
                CodexMessageRole.SYSTEM to CodexMessageKind.FILE_CHANGE
            loweredItemType in setOf("toolcall", "tool_call") ->
                CodexMessageRole.SYSTEM to (decodedToolCall?.first ?: CodexMessageKind.TOOL_ACTIVITY)
            loweredItemType in setOf("commandexecution", "command_execution") ->
                CodexMessageRole.SYSTEM to CodexMessageKind.COMMAND_EXECUTION
            isSubagentItemType(loweredItemType) ->
                CodexMessageRole.SYSTEM to CodexMessageKind.SUBAGENT_ACTION
            loweredItemType in setOf("userinputprompt", "user_input_prompt", "requestuserinput", "request_user_input") ->
                CodexMessageRole.SYSTEM to CodexMessageKind.USER_INPUT_PROMPT
            loweredItemType == "plan" ->
                CodexMessageRole.SYSTEM to CodexMessageKind.PLAN
            loweredItemType in setOf("enteredreviewmode", "entered_review_mode") ->
                CodexMessageRole.SYSTEM to CodexMessageKind.CHAT
            loweredItemType in setOf("exitedreviewmode", "exited_review_mode") ->
                CodexMessageRole.ASSISTANT to CodexMessageKind.CHAT
            else ->
                CodexMessageRole.SYSTEM to CodexMessageKind.CHAT
        }

        val text = decodedToolCall?.second ?: decodeItemText(item, itemType)
        if (loweredItemType in setOf("toolcall", "tool_call") && text.isBlank()) {
            return null
        }
        if (text.isBlank() && kind == CodexMessageKind.CHAT && role != CodexMessageRole.USER) {
            return null // Skip empty assistant messages
        }

        val attachments = decodeImageAttachments(item)
        val commandDetails = if (kind == CodexMessageKind.COMMAND_EXECUTION) {
            decodeCommandDetails(item)
        } else null
        val planState = if (kind == CodexMessageKind.PLAN) {
            decodePlanState(item)
        } else null
        val subagentAction = if (kind == CodexMessageKind.SUBAGENT_ACTION) {
            decodeSubagentAction(item, itemType)
        } else null
        val structuredUserInputRequest = if (kind == CodexMessageKind.USER_INPUT_PROMPT) {
            decodeStructuredUserInputRequest(item)
        } else null

        return CodexMessage(
            id = stableHistoryMessageId(
                threadId = threadId,
                turnId = turnId,
                itemId = itemId,
                role = role,
                kind = kind,
                text = text,
                attachments = attachments,
                offsetIndex = offsetIndex
            ),
            threadId = threadId,
            role = role,
            kind = kind,
            text = text,
            createdAt = CodexThread.parseTimestamp(itemTimestamp),
            turnId = turnId,
            itemId = itemId,
            orderIndex = CodexMessageOrderCounter.next(),
            attachments = attachments,
            planState = planState,
            subagentAction = subagentAction,
            structuredUserInputRequest = structuredUserInputRequest,
            commandDetails = commandDetails,
            deliveryState = CodexMessageDeliveryState.CONFIRMED
        )
    }

    private fun decodeItemText(item: Map<String, JsonValue>, itemType: String): String {
        decodeContentText(item)?.let { return it }

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

        if (itemType.lowercase() in listOf("toolcall", "tool_call")) {
            decodeToolCallMessage(item)?.second?.let { return it }
        }

        // Command execution
        if (itemType.lowercase() in listOf("commandexecution", "command_execution")) {
            return decodeCommandExecutionText(item)
        }

        if (itemType.lowercase() == "plan") {
            return decodePlanItemText(item)
        }

        if (itemType.lowercase() in listOf("enteredreviewmode", "entered_review_mode")) {
            val reviewLabel = deepFirstStringValue(item, "review", "label", "title")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "changes"
            return "Reviewing $reviewLabel..."
        }

        if (itemType.lowercase() in listOf("exitedreviewmode", "exited_review_mode")) {
            deepFirstStringValue(item, "review", "text", "message", "content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        if (itemType.lowercase() in listOf(
                "userinputprompt",
                "user_input_prompt",
                "requestuserinput",
                "request_user_input"
            )
        ) {
            return decodeStructuredUserInputSummary(item)
        }

        if (isSubagentItemType(itemType.lowercase())) {
            return decodeSubagentAction(item, itemType)?.summaryText ?: ""
        }

        return ""
    }

    fun decodeToolCallMessage(item: Map<String, JsonValue>): Pair<CodexMessageKind, String>? {
        decodeToolCallFileChangeText(item)?.let { return CodexMessageKind.FILE_CHANGE to it }
        decodeToolActivityText(item)?.let { return CodexMessageKind.TOOL_ACTIVITY to it }
        return null
    }

    fun decodeToolActivityText(item: Map<String, JsonValue>): String? {
        val output = deepFirstStringValue(
            item,
            "text",
            "message",
            "summary",
            "stdout",
            "stderr",
            "output_text",
            "outputText"
        )
        if (!output.isNullOrBlank()) {
            val acceptedPrefixes = listOf(
                "running ",
                "read ",
                "search ",
                "searched ",
                "exploring ",
                "list ",
                "listing ",
                "open ",
                "opened ",
                "find ",
                "finding ",
                "edit ",
                "edited ",
                "write ",
                "wrote ",
                "apply ",
                "applied "
            )
            val activityLines = output
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 140 }
                .filter { line ->
                    val lower = line.lowercase()
                    acceptedPrefixes.any(lower::startsWith)
                }
                .toList()
            if (activityLines.isNotEmpty()) {
                return activityLines.joinToString("\n")
            }
        }

        val nestedTool = item["tool"]?.objectValue
        val nestedCall = item["call"]?.objectValue
        val descriptor = firstNonEmptyString(
            firstStringValue(item, "kind", "name", "tool", "tool_name", "toolName", "title"),
            firstStringValue(nestedTool ?: emptyMap(), "kind", "name", "type", "title"),
            firstStringValue(nestedCall ?: emptyMap(), "kind", "name", "type", "title")
        )
        val summary = toolActivitySummaryLine(
            descriptor = descriptor,
            rawStatus = firstNonEmptyString(
                firstStringValue(item, "status", "phase", "state"),
                firstStringValue(nestedTool ?: emptyMap(), "status", "phase", "state"),
                firstStringValue(nestedCall ?: emptyMap(), "status", "phase", "state")
            ),
            isCompleted = true
        )
        return normalizeOptionalText(summary)
    }

    private fun decodeToolCallFileChangeText(item: Map<String, JsonValue>): String? {
        val directFileChange = decodeFileChangeText(item).takeIf { it.isNotBlank() && !it.endsWith(":") }
        if (directFileChange != null && directFileChange != "modified:") {
            return directFileChange
        }

        val changeLines = decodeNestedFileChangeLines(
            deepFirstJsonValue(
                item,
                "changes",
                "file_changes",
                "fileChanges",
                "files",
                "edits",
                "modified_files",
                "modifiedFiles",
                "patches"
            )
        )
        if (changeLines.isNotEmpty()) {
            return changeLines.joinToString("\n")
        }

        val diff = normalizeOptionalText(
            deepFirstStringValue(item, "diff", "unified_diff", "unifiedDiff", "patch")
        )
        if (diff != null) {
            val status = normalizeOptionalText(
                deepFirstStringValue(item, "status", "phase", "state")
            ) ?: "completed"
            return "Status: $status\n\n```diff\n$diff\n```"
        }

        return null
    }

    private fun decodeNestedFileChangeLines(value: JsonValue?): List<String> {
        val entries = mutableListOf<String>()

        fun appendEntry(pathHint: String?, objectValue: Map<String, JsonValue>?) {
            if (objectValue == null) return
            val path = normalizeOptionalText(
                firstStringValue(objectValue, "path", "filePath", "file_path", "name") ?: pathHint
            ) ?: return
            val kind = normalizeOptionalText(
                firstStringValue(objectValue, "kind", "action", "changeType", "change_type", "status")
            ) ?: "modified"
            val additions = objectValue["additions"]?.intValue
            val deletions = objectValue["deletions"]?.intValue
            val totals = if ((additions ?: 0) > 0 || (deletions ?: 0) > 0) {
                " (+${additions ?: 0}/-${deletions ?: 0})"
            } else {
                ""
            }
            entries += "$kind: $path$totals"
        }

        value?.arrayValue?.forEach { candidate ->
            appendEntry(pathHint = null, objectValue = candidate.objectValue)
        }
        value?.objectValue?.forEach { (key, candidate) ->
            appendEntry(pathHint = key, objectValue = candidate.objectValue)
        }

        return entries.distinct()
    }

    private fun toolActivitySummaryLine(
        descriptor: String?,
        rawStatus: String?,
        isCompleted: Boolean
    ): String {
        val normalizedDescriptor = normalizeOptionalText(descriptor)
        val normalizedStatus = normalizeOptionalText(rawStatus)?.lowercase()
        val lead = when (normalizedStatus) {
            "queued", "pending" -> "Queued"
            "running", "in_progress", "inprogress", "started" -> "Running"
            "failed", "error" -> "Failed"
            "cancelled", "canceled" -> "Cancelled"
            "completed", "done", "success", "succeeded" -> "Completed"
            else -> if (isCompleted) "Completed" else "Running"
        }

        return normalizedDescriptor?.let { "$lead $it" } ?: lead
    }

    private fun decodeContentText(item: Map<String, JsonValue>): String? {
        val contentItems = item["content"]?.arrayValue ?: return null
        val textParts = buildList {
            for (value in contentItems) {
                val objectValue = value.objectValue ?: continue
                when (normalizedContentType(firstStringValue(objectValue, "type"))) {
                    "text", "inputtext", "outputtext", "message" -> {
                        normalizeOptionalText(firstStringValue(objectValue, "text"))?.let(::add)
                        normalizeOptionalText(
                            objectValue["data"]?.objectValue?.get("text")?.stringValue
                        )?.let(::add)
                    }
                    "skill" -> {
                        val resolvedSkill = normalizeOptionalText(
                            firstStringValue(objectValue, "id", "name")
                        )
                        if (resolvedSkill != null) {
                            add("$$resolvedSkill")
                        }
                    }
                }
            }
        }

        val joined = textParts.joinToString("\n").trim()
        return joined.ifEmpty { null }
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
        val contentAttachments = (item["content"]?.arrayValue ?: emptyList()).mapNotNull { value ->
            val objectValue = value.objectValue ?: return@mapNotNull null
            val type = normalizedContentType(firstStringValue(objectValue, "type"))
            if (type != "image" && type != "localimage") {
                return@mapNotNull null
            }

            val sourceURL = firstStringValue(objectValue, "url", "image_url", "path")
            val payloadDataURL = sourceURL?.takeIf { it.startsWith("data:image", ignoreCase = true) }
            CodexImageAttachment(
                payloadDataURL = payloadDataURL,
                sourceURL = sourceURL
            )
        }
        if (contentAttachments.isNotEmpty()) {
            return contentAttachments
        }

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

    fun decodePlanItemText(item: Map<String, JsonValue>): String {
        val explanation = normalizeOptionalText(firstStringValue(item, "explanation"))
        if (explanation != null) {
            return explanation
        }

        val summary = normalizeOptionalText(firstStringValue(item, "summary"))
        if (summary != null) {
            return summary
        }

        return "Planning..."
    }

    fun decodePlanState(item: Map<String, JsonValue>): CodexPlanState? {
        val explanation = normalizeOptionalText(firstStringValue(item, "explanation", "summary"))
        val steps = (item["plan"]?.arrayValue ?: emptyList()).mapNotNull { stepValue ->
            val stepObject = stepValue.objectValue ?: return@mapNotNull null
            val step = normalizeOptionalText(firstStringValue(stepObject, "step")) ?: return@mapNotNull null
            val rawStatus = normalizeOptionalText(firstStringValue(stepObject, "status")) ?: return@mapNotNull null
            val status = when (rawStatus.lowercase()) {
                "pending" -> CodexPlanStepStatus.PENDING
                "in_progress", "inprogress" -> CodexPlanStepStatus.IN_PROGRESS
                "completed" -> CodexPlanStepStatus.COMPLETED
                else -> null
            } ?: return@mapNotNull null

            CodexPlanStep(step = step, status = status)
        }

        if (explanation == null && steps.isEmpty()) {
            return null
        }

        return CodexPlanState(
            explanation = explanation,
            steps = steps
        )
    }

    fun decodeStructuredUserInputRequest(
        item: Map<String, JsonValue>,
        fallbackRequestID: JsonValue? = null
    ): CodexStructuredUserInputRequest? {
        val questions = decodeStructuredUserInputQuestions(item["questions"])
        if (questions.isEmpty()) {
            return null
        }

        val requestID = firstJsonValue(item, "requestId", "request_id", "id")
            ?: fallbackRequestID
            ?: JsonValue.string("request-${questions.joinToString("-") { it.id }}")

        return CodexStructuredUserInputRequest(
            requestID = requestID,
            questions = questions
        )
    }

    fun decodeStructuredUserInputQuestions(value: JsonValue?): List<CodexStructuredUserInputQuestion> {
        val items = value?.arrayValue ?: emptyList()
        return items.mapNotNull { questionValue ->
            val questionObject = questionValue.objectValue ?: return@mapNotNull null
            val id = normalizeOptionalText(firstStringValue(questionObject, "id")) ?: return@mapNotNull null
            val question = normalizeOptionalText(firstStringValue(questionObject, "question"))
                ?: return@mapNotNull null

            val options = (questionObject["options"]?.arrayValue ?: emptyList()).mapNotNull optionMap@{ optionValue ->
                val optionObject = optionValue.objectValue ?: return@optionMap null
                val label = normalizeOptionalText(firstStringValue(optionObject, "label"))
                    ?: return@optionMap null
                CodexStructuredUserInputOption(
                    id = normalizeOptionalText(firstStringValue(optionObject, "id")) ?: label,
                    label = label,
                    description = normalizeOptionalText(firstStringValue(optionObject, "description"))
                )
            }

            CodexStructuredUserInputQuestion(
                id = id,
                header = normalizeOptionalText(firstStringValue(questionObject, "header")),
                question = question,
                isOther = firstBooleanValue(questionObject, "isOther", "is_other") ?: false,
                isSecret = firstBooleanValue(questionObject, "isSecret", "is_secret") ?: false,
                options = options
            )
        }
    }

    fun decodeStructuredUserInputSummary(item: Map<String, JsonValue>): String {
        val firstQuestion = decodeStructuredUserInputQuestions(item["questions"]).firstOrNull()?.question
        return firstQuestion ?: "Input requested"
    }

    fun decodeSubagentAction(
        item: Map<String, JsonValue>,
        itemType: String? = null
    ): CodexSubagentAction? {
        val receiverThreadIds = buildList {
            normalizeOptionalText(firstStringValue(item, "receiverThreadId", "receiver_thread_id"))?.let(::add)
            normalizeOptionalText(firstStringValue(item, "newThreadId", "new_thread_id"))?.let(::add)
            (firstJsonValue(item, "receiverThreadIds", "receiver_thread_ids")?.arrayValue ?: emptyList())
                .mapNotNullTo(this) { normalizeOptionalText(it.stringValue) }
        }.distinct()

        val receiverAgents = (firstJsonValue(item, "receiverAgents", "receiver_agents")?.arrayValue ?: emptyList())
            .mapNotNull { agentValue ->
                val agentObject = agentValue.objectValue ?: return@mapNotNull null
                val threadId = normalizeOptionalText(
                    firstStringValue(agentObject, "threadId", "thread_id")
                ) ?: return@mapNotNull null
                CodexSubagentRef(
                    agentId = normalizeOptionalText(firstStringValue(agentObject, "agentId", "agent_id")),
                    nickname = normalizeOptionalText(firstStringValue(agentObject, "nickname")),
                    role = normalizeOptionalText(firstStringValue(agentObject, "role")),
                    model = normalizeOptionalText(firstStringValue(agentObject, "model")),
                    threadId = threadId
                )
            }

        val agentStates = (firstJsonValue(item, "agentStates", "agent_states")?.arrayValue ?: emptyList())
            .mapNotNull { stateValue ->
                val stateObject = stateValue.objectValue ?: return@mapNotNull null
                CodexSubagentState(
                    agentId = normalizeOptionalText(firstStringValue(stateObject, "agentId", "agent_id")),
                    status = normalizeOptionalText(firstStringValue(stateObject, "status")) ?: return@mapNotNull null,
                    message = normalizeOptionalText(firstStringValue(stateObject, "message"))
                )
            }

        val tool = normalizeOptionalText(firstStringValue(item, "tool", "name"))
            ?: inferSubagentTool(itemType)
        val status = normalizeOptionalText(firstStringValue(item, "status")) ?: "in_progress"
        val prompt = normalizeOptionalText(firstStringValue(item, "prompt", "task", "message"))
        val model = normalizeOptionalText(
            firstStringValue(
                item,
                "model",
                "modelName",
                "model_name",
                "requestedModel",
                "requested_model"
            )
        )

        if (tool == null
            && receiverThreadIds.isEmpty()
            && receiverAgents.isEmpty()
            && agentStates.isEmpty()
            && prompt == null
        ) {
            return null
        }

        return CodexSubagentAction(
            tool = tool,
            status = status,
            prompt = prompt,
            model = model,
            receiverThreadIds = receiverThreadIds,
            receiverAgents = receiverAgents,
            agentStates = agentStates
        )
    }

    private fun inferSubagentTool(itemType: String?): String? {
        return when (itemType?.trim()?.lowercase()) {
            "collabtoolcall", "collab_tool_call", "subagentaction", "subagent_action" -> "spawnAgent"
            "collabwaiting", "waitagent" -> "waitAgent"
            "collabclose", "closeagent" -> "closeAgent"
            "collabresume", "resumeagent" -> "resumeAgent"
            else -> null
        }
    }

    private fun isSubagentItemType(itemType: String): Boolean {
        return itemType == "subagentaction"
            || itemType == "subagent_action"
            || itemType == "collabtoolcall"
            || itemType == "collab_tool_call"
            || itemType.startsWith("collabagentspawn")
            || itemType.startsWith("collabwaiting")
            || itemType.startsWith("collabclose")
            || itemType.startsWith("collabresume")
            || itemType.startsWith("collabagentinteraction")
    }

    private fun firstStringValue(item: Map<String, JsonValue>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> item[key]?.stringValue }
    }

    private fun firstJsonValue(item: Map<String, JsonValue>, vararg keys: String): JsonValue? {
        return keys.firstNotNullOfOrNull { key -> item[key] }
    }

    private fun firstBooleanValue(item: Map<String, JsonValue>, vararg keys: String): Boolean? {
        return keys.firstNotNullOfOrNull { key -> item[key]?.boolValue }
    }

    private fun normalizeOptionalText(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun stableHistoryMessageId(
        threadId: String,
        turnId: String?,
        itemId: String?,
        role: CodexMessageRole,
        kind: CodexMessageKind,
        text: String,
        attachments: List<CodexImageAttachment>,
        offsetIndex: Int
    ): String {
        val normalizedItemId = normalizeOptionalText(itemId)
        if (normalizedItemId != null) {
            return "history:$threadId:${role.name}:${kind.name}:$normalizedItemId"
        }

        val attachmentSignature = attachments.joinToString(separator = "|") { attachment ->
            attachment.payloadDataURL
                ?: attachment.sourceURL
                ?: attachment.thumbnailBase64JPEG
                ?: ""
        }
        val textFingerprint = buildString {
            append(turnId ?: "no-turn")
            append('|')
            append(text.trim())
            append('|')
            append(attachmentSignature)
            append('|')
            append(offsetIndex)
        }
        return "history:$threadId:${role.name}:${kind.name}:${textFingerprint.hashCode().toUInt().toString(16)}"
    }

    private fun firstNonEmptyString(vararg values: String?): String? {
        return values.firstNotNullOfOrNull { normalizeOptionalText(it) }
    }

    private fun deepFirstStringValue(item: Map<String, JsonValue>, vararg keys: String): String? {
        return collectCandidateObjects(item)
            .firstNotNullOfOrNull { candidate -> firstStringValue(candidate, *keys) }
    }

    private fun deepFirstJsonValue(item: Map<String, JsonValue>, vararg keys: String): JsonValue? {
        return collectCandidateObjects(item)
            .firstNotNullOfOrNull { candidate -> firstJsonValue(candidate, *keys) }
    }

    private fun collectCandidateObjects(
        item: Map<String, JsonValue>,
        maxDepth: Int = 3
    ): List<Map<String, JsonValue>> {
        val candidates = mutableListOf<Map<String, JsonValue>>()
        val seen = mutableSetOf<Int>()

        fun visit(value: JsonValue?, depth: Int) {
            if (value == null || depth > maxDepth) return

            when (value) {
                is JsonValue.ObjectValue -> {
                    val objectValue = value.value
                    val identity = System.identityHashCode(objectValue)
                    if (!seen.add(identity)) return
                    candidates += objectValue
                    objectValue.values.forEach { nested ->
                        visit(nested, depth + 1)
                    }
                }
                is JsonValue.ArrayValue -> {
                    value.value.forEach { nested ->
                        visit(nested, depth + 1)
                    }
                }
                else -> Unit
            }
        }

        visit(JsonValue.ObjectValue(item), depth = 0)
        return candidates
    }

    private fun normalizedContentType(value: String?): String {
        return value
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?: ""
    }
}
