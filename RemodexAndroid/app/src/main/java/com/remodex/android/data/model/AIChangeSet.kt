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
    var unsupportedReasons: List<String> = emptyList(),
    var revertMetadata: AIRevertMetadata = AIRevertMetadata(),
    var fallbackPatchCount: Int = 0
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

@Serializable
data class AIRevertMetadata(
    val revertedAtEpochMs: Long? = null,
    val revertAttemptedAtEpochMs: Long? = null,
    val lastRevertError: String? = null
)

@Serializable
data class RevertConflict(
    val path: String,
    val message: String
)

@Serializable
data class RevertPreviewResult(
    val canRevert: Boolean = false,
    val affectedFiles: List<String> = emptyList(),
    val conflicts: List<RevertConflict> = emptyList(),
    val unsupportedReasons: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList()
)

@Serializable
data class RevertApplyResult(
    val success: Boolean = false,
    val revertedFiles: List<String> = emptyList(),
    val conflicts: List<RevertConflict> = emptyList(),
    val unsupportedReasons: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList(),
    val status: GitRepoSyncResult? = null
)

enum class AssistantRevertRiskLevel {
    SAFE, WARNING, BLOCKED
}

data class AssistantRevertPresentation(
    val title: String,
    val isEnabled: Boolean,
    val helperText: String? = null,
    val riskLevel: AssistantRevertRiskLevel = AssistantRevertRiskLevel.SAFE,
    val warningText: String? = null,
    val overlappingFiles: List<String> = emptyList()
)

@Serializable
data class AIChangeSetLedgerSnapshot(
    val changeSets: List<AIChangeSet> = emptyList(),
    val changeSetIdByAssistantMessageId: Map<String, String> = emptyMap(),
    val changeSetIdByTurnId: Map<String, String> = emptyMap(),
    val repoRootByWorkingDirectory: Map<String, String> = emptyMap()
)

object AIUnifiedPatchParser {
    data class PatchAnalysis(
        val fileChanges: List<AIFileChange>,
        val unsupportedReasons: List<String>
    )

    fun analyze(rawPatch: String): PatchAnalysis {
        val patch = rawPatch.trim()
        if (patch.isEmpty()) {
            return PatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured.")
            )
        }

        val chunks = splitIntoChunks(patch)
        if (chunks.isEmpty()) {
            return PatchAnalysis(
                fileChanges = emptyList(),
                unsupportedReasons = listOf("No exact patch was captured.")
            )
        }

        val fileChanges = mutableListOf<AIFileChange>()
        val unsupportedReasons = linkedSetOf<String>()
        chunks.forEach { chunk ->
            val analysis = analyzeChunk(chunk)
            analysis.fileChange?.let(fileChanges::add)
            unsupportedReasons.addAll(analysis.unsupportedReasons)
        }
        if (fileChanges.isEmpty()) {
            unsupportedReasons += "No exact patch was captured."
        }

        return PatchAnalysis(
            fileChanges = fileChanges,
            unsupportedReasons = unsupportedReasons.toList().sorted()
        )
    }

    fun hash(patch: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(patch.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private data class PatchChunkAnalysis(
        val fileChange: AIFileChange?,
        val unsupportedReasons: Set<String>
    )

    private fun splitIntoChunks(patch: String): List<List<String>> {
        val lines = patch.split('\n')
        if (lines.isEmpty()) {
            return emptyList()
        }

        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                chunks += current.toList()
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) {
            chunks += current.toList()
        }
        return chunks
    }

    private fun analyzeChunk(lines: List<String>): PatchChunkAnalysis {
        if (lines.isEmpty()) {
            return PatchChunkAnalysis(fileChange = null, unsupportedReasons = emptySet())
        }

        val path = extractPath(lines)
        val isBinary = lines.any { it.startsWith("Binary files ") || it == "GIT binary patch" }
        val isRenameOrModeOnly = lines.any {
            it.startsWith("rename from ")
                || it.startsWith("rename to ")
                || it.startsWith("copy from ")
                || it.startsWith("copy to ")
                || it.startsWith("old mode ")
                || it.startsWith("new mode ")
                || it.startsWith("new file mode 120")
                || it.startsWith("deleted file mode 120")
                || it.startsWith("similarity index ")
        }
        val isCreate = lines.contains("new file mode 100644")
            || lines.contains("new file mode 100755")
            || lines.contains("--- /dev/null")
        val isDelete = lines.contains("deleted file mode 100644")
            || lines.contains("deleted file mode 100755")
            || lines.contains("+++ /dev/null")

        var additions = 0
        var deletions = 0
        lines.forEach { line ->
            when {
                line.startsWith("+") && !line.startsWith("+++") -> additions += 1
                line.startsWith("-") && !line.startsWith("---") -> deletions += 1
            }
        }

        val unsupportedReasons = linkedSetOf<String>()
        if (isBinary) {
            unsupportedReasons += "Binary changes are not auto-revertable in v1."
        }
        if (isRenameOrModeOnly) {
            unsupportedReasons += "Rename, mode-only, or symlink changes are not auto-revertable in v1."
        }

        val hasPatchBody = additions > 0 || deletions > 0 || isCreate || isDelete
        if (path.isBlank() || !hasPatchBody) {
            if (!isBinary && !isRenameOrModeOnly) {
                unsupportedReasons += "No exact patch was captured."
            }
            return PatchChunkAnalysis(fileChange = null, unsupportedReasons = unsupportedReasons)
        }

        val kind = when {
            isCreate -> AIFileChangeKind.CREATE
            isDelete -> AIFileChangeKind.DELETE
            else -> AIFileChangeKind.UPDATE
        }
        return PatchChunkAnalysis(
            fileChange = AIFileChange(
                path = path,
                kind = kind,
                additions = additions,
                deletions = deletions,
                isBinary = isBinary,
                isRenameOrModeOnly = isRenameOrModeOnly
            ),
            unsupportedReasons = unsupportedReasons
        )
    }

    private fun extractPath(lines: List<String>): String {
        lines.forEach { line ->
            if (line.startsWith("+++ ")) {
                val normalized = normalizeDiffPath(line.removePrefix("+++ ").trim())
                if (normalized.isNotEmpty() && normalized != "/dev/null") {
                    return normalized
                }
            }
        }

        lines.forEach { line ->
            if (line.startsWith("diff --git ")) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val normalized = normalizeDiffPath(parts[3])
                    if (normalized.isNotEmpty()) {
                        return normalized
                    }
                }
            }
        }
        return ""
    }

    private fun normalizeDiffPath(rawPath: String): String {
        var value = rawPath.trim()
        if (value.startsWith("a/") || value.startsWith("b/")) {
            value = value.drop(2)
        }
        return value
    }
}
