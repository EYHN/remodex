package com.remodex.android.data.model

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class CodexThread(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val preview: String? = null,
    @SerialName("created_at") val createdAtRaw: String? = null,
    @SerialName("updated_at") val updatedAtRaw: String? = null,
    val cwd: String? = null,
    val metadata: JsonValue? = null,
    @SerialName("forked_from_thread_id") val forkedFromThreadId: String? = null,
    @SerialName("parent_thread_id") val parentThreadId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("agent_nickname") val agentNickname: String? = null,
    @SerialName("agent_role") val agentRole: String? = null,
    val model: String? = null,
    @SerialName("model_provider") val modelProvider: String? = null,
    var syncState: CodexThreadSyncState = CodexThreadSyncState.LIVE
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "New conversation"

    val isSubagent: Boolean
        get() = agentId != null || parentThreadId != null

    val isForkedThread: Boolean
        get() = forkedFromThreadId != null

    val isManagedWorktreeProject: Boolean
        get() = isManagedWorktreeProjectPath(projectKey)

    val projectKey: String?
        get() = normalizeProjectPath(cwd)

    val projectDisplayName: String?
        get() {
            val path = projectKey ?: return null
            return path.substringAfterLast('/')
        }

    val gitWorkingDirectory: String?
        get() = cwd

    val createdAt: Long
        get() = parseTimestamp(createdAtRaw)

    val updatedAt: Long
        get() = parseTimestamp(updatedAtRaw)

    val modelDisplayLabel: String?
        get() = model?.let { m ->
            when {
                m.contains("o4-mini") -> "o4-mini"
                m.contains("o3") -> "o3"
                m.contains("gpt-4") -> "GPT-4"
                else -> m.substringAfterLast("/").substringBefore("-202")
            }
        }

    companion object {
        fun normalizeProjectPath(path: String?): String? {
            val trimmed = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed == "/") return "/"

            var normalized = trimmed
            while (normalized.endsWith("/")) {
                normalized = normalized.dropLast(1)
            }

            return if (normalized.isEmpty()) "/" else normalized
        }

        private fun codexManagedWorktreeToken(normalizedProjectPath: String?): String? {
            val path = normalizedProjectPath ?: return null
            val components = path.split('/').filter { it.isNotEmpty() }
            val worktreesIndex = components.indexOf("worktrees")
            if (worktreesIndex <= 0) return null
            if (components[worktreesIndex - 1] != ".codex") return null

            val tokenIndex = worktreesIndex + 1
            val token = components.getOrNull(tokenIndex)?.trim().orEmpty()
            return token.takeIf { it.isNotEmpty() }
        }

        fun managedWorktreeTokenForProjectPath(path: String?): String? =
            codexManagedWorktreeToken(normalizeProjectPath(path))

        fun isManagedWorktreeProjectPath(path: String?): Boolean =
            managedWorktreeTokenForProjectPath(path) != null

        fun parseTimestamp(raw: String?): Long {
            if (raw == null) return 0L
            return try {
                // Try Unix seconds (numeric)
                val num = raw.toDoubleOrNull()
                if (num != null) {
                    if (num > 1_000_000_000_000) num.toLong() // Already milliseconds
                    else (num * 1000).toLong() // Seconds to milliseconds
                } else {
                    // Try ISO 8601
                    java.time.Instant.parse(raw).toEpochMilli()
                }
            } catch (_: Exception) { 0L }
        }

        private fun readStringValue(obj: JsonObject?, key: String): String? {
            val primitive = obj?.get(key) as? JsonPrimitive ?: return null
            return primitive.contentOrNull?.takeIf { it.isNotBlank() }
        }

        private fun readThreadIdentity(
            obj: JsonObject,
            metadata: JsonObject?,
            vararg keys: String
        ): String? {
            for (key in keys) {
                readStringValue(obj, key)?.let { return it }
            }

            for (key in keys) {
                readStringValue(metadata, key)?.let { return it }
            }

            return null
        }

        fun fromJson(json: Json, element: JsonElement): CodexThread? {
            if (element !is JsonObject) return null
            val obj = element
            val id = (obj["id"] ?: obj["thread_id"])?.jsonPrimitive?.contentOrNull ?: return null
            val metadataElement = obj["metadata"]
            val metadataObject = metadataElement as? JsonObject
            val syncState = when (
                (obj["syncState"] ?: obj["sync_state"])?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.lowercase()
            ) {
                "archivedlocal", "archived_local" -> CodexThreadSyncState.ARCHIVED_LOCAL
                else -> if ((obj["archived"] ?: obj["is_archived"])?.jsonPrimitive?.booleanOrNull == true) {
                    CodexThreadSyncState.ARCHIVED_LOCAL
                } else {
                    CodexThreadSyncState.LIVE
                }
            }
            return CodexThread(
                id = id,
                title = (obj["title"] ?: obj["name"])?.jsonPrimitive?.contentOrNull,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                preview = obj["preview"]?.jsonPrimitive?.contentOrNull,
                createdAtRaw = (obj["created_at"] ?: obj["createdAt"])?.jsonPrimitive?.contentOrNull,
                updatedAtRaw = (obj["updated_at"] ?: obj["updatedAt"])?.jsonPrimitive?.contentOrNull,
                cwd = (obj["cwd"] ?: obj["working_directory"])?.jsonPrimitive?.contentOrNull,
                metadata = metadataElement?.let { JsonValue.from(it) },
                forkedFromThreadId = readThreadIdentity(
                    obj = obj,
                    metadata = metadataObject,
                    "forked_from_thread_id",
                    "forkedFromThreadId",
                    "forked_from_id",
                    "forkedFromId"
                ),
                parentThreadId = (obj["parent_thread_id"] ?: obj["parentThreadId"])?.jsonPrimitive?.contentOrNull,
                agentId = (obj["agent_id"] ?: obj["agentId"])?.jsonPrimitive?.contentOrNull,
                agentNickname = (obj["agent_nickname"] ?: obj["agentNickname"])?.jsonPrimitive?.contentOrNull,
                agentRole = (obj["agent_role"] ?: obj["agentRole"])?.jsonPrimitive?.contentOrNull,
                model = obj["model"]?.jsonPrimitive?.contentOrNull,
                modelProvider = (obj["model_provider"] ?: obj["modelProvider"])?.jsonPrimitive?.contentOrNull,
                syncState = syncState
            )
        }
    }
}

enum class CodexThreadSyncState {
    LIVE, ARCHIVED_LOCAL
}
