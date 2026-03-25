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
            if (path.isNullOrBlank()) return null
            return path.trimEnd('/')
        }

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

        fun fromJson(json: Json, element: JsonElement): CodexThread? {
            if (element !is JsonObject) return null
            val obj = element
            val id = (obj["id"] ?: obj["thread_id"])?.jsonPrimitive?.contentOrNull ?: return null
            return CodexThread(
                id = id,
                title = (obj["title"] ?: obj["name"])?.jsonPrimitive?.contentOrNull,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                preview = obj["preview"]?.jsonPrimitive?.contentOrNull,
                createdAtRaw = (obj["created_at"] ?: obj["createdAt"])?.jsonPrimitive?.contentOrNull,
                updatedAtRaw = (obj["updated_at"] ?: obj["updatedAt"])?.jsonPrimitive?.contentOrNull,
                cwd = (obj["cwd"] ?: obj["working_directory"])?.jsonPrimitive?.contentOrNull,
                metadata = obj["metadata"]?.let { JsonValue.from(it) },
                forkedFromThreadId = (obj["forked_from_thread_id"] ?: obj["forkedFromThreadId"])?.jsonPrimitive?.contentOrNull,
                parentThreadId = (obj["parent_thread_id"] ?: obj["parentThreadId"])?.jsonPrimitive?.contentOrNull,
                agentId = (obj["agent_id"] ?: obj["agentId"])?.jsonPrimitive?.contentOrNull,
                agentNickname = (obj["agent_nickname"] ?: obj["agentNickname"])?.jsonPrimitive?.contentOrNull,
                agentRole = (obj["agent_role"] ?: obj["agentRole"])?.jsonPrimitive?.contentOrNull,
                model = obj["model"]?.jsonPrimitive?.contentOrNull,
                modelProvider = (obj["model_provider"] ?: obj["modelProvider"])?.jsonPrimitive?.contentOrNull
            )
        }
    }
}

enum class CodexThreadSyncState {
    LIVE, ARCHIVED_LOCAL
}
