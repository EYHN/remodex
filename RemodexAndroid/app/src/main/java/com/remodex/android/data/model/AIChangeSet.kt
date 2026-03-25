package com.remodex.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AIChangeSet(
    val id: String,
    val repoRoot: String? = null,
    val threadId: String? = null,
    val turnId: String? = null,
    val assistantMessageId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var finalizedAt: Long? = null,
    var status: AIChangeSetStatus = AIChangeSetStatus.COLLECTING,
    val source: AIChangeSetSource = AIChangeSetSource.TURN_DIFF,
    var forwardUnifiedPatch: String? = null,
    var inverseUnifiedPatch: String? = null,
    var patchHash: String? = null,
    var fileChanges: List<AIFileChange> = emptyList(),
    var unsupportedReasons: List<String> = emptyList()
)

@Serializable
enum class AIChangeSetStatus {
    COLLECTING, READY, REVERTED, FAILED, NOT_REVERTABLE
}

@Serializable
enum class AIChangeSetSource {
    TURN_DIFF, FILE_CHANGE_FALLBACK
}

@Serializable
data class AIFileChange(
    val path: String,
    val kind: AIFileChangeKind = AIFileChangeKind.UPDATE,
    val additions: Int = 0,
    val deletions: Int = 0,
    val isBinary: Boolean = false,
    val isRenameOrModeOnly: Boolean = false,
    val beforeContentHash: String? = null,
    val afterContentHash: String? = null
)

@Serializable
enum class AIFileChangeKind {
    CREATE, UPDATE, DELETE
}

object AIUnifiedPatchParser {
    data class PatchAnalysis(
        val fileChanges: List<AIFileChange>,
        val unsupportedReasons: List<String>
    )

    fun analyze(rawPatch: String): PatchAnalysis {
        val changes = mutableListOf<AIFileChange>()
        val unsupported = mutableListOf<String>()
        var currentPath: String? = null
        var additions = 0
        var deletions = 0
        var kind = AIFileChangeKind.UPDATE

        for (line in rawPatch.lines()) {
            when {
                line.startsWith("diff --git") -> {
                    currentPath?.let {
                        changes.add(AIFileChange(it, kind, additions, deletions))
                    }
                    currentPath = line.substringAfter(" b/").trim()
                    additions = 0
                    deletions = 0
                    kind = AIFileChangeKind.UPDATE
                }
                line.startsWith("new file") -> kind = AIFileChangeKind.CREATE
                line.startsWith("deleted file") -> kind = AIFileChangeKind.DELETE
                line.startsWith("+") && !line.startsWith("+++") -> additions++
                line.startsWith("-") && !line.startsWith("---") -> deletions++
                line.startsWith("Binary files") -> {
                    currentPath?.let {
                        changes.add(AIFileChange(it, kind, 0, 0, isBinary = true))
                    }
                    currentPath = null
                }
            }
        }
        currentPath?.let {
            changes.add(AIFileChange(it, kind, additions, deletions))
        }

        return PatchAnalysis(changes, unsupported)
    }

    fun hash(patch: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(patch.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
