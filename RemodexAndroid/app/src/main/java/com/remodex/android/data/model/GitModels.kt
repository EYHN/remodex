package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitRepoSyncResult(
    val repoRoot: String? = null,
    val currentBranch: String? = null,
    val trackingBranch: String? = null,
    val isDirty: Boolean = false,
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val localOnlyCommitCount: Int = 0,
    val state: String? = null,
    val canPush: Boolean = false,
    val isPublishedToRemote: Boolean = false,
    val files: List<GitChangedFile> = emptyList(),
    val repoDiffTotals: GitDiffTotals? = null
)

@Serializable
data class GitDiffTotals(
    val additions: Int = 0,
    val deletions: Int = 0
)

@Serializable
data class GitChangedFile(
    val path: String,
    val status: String? = null,
    val staged: Boolean = false
)

@Serializable
data class GitCommitResult(
    val success: Boolean = false,
    val commitHash: String? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class GitPushResult(
    val success: Boolean = false,
    val remote: String? = null,
    val branch: String? = null,
    val error: String? = null
)

@Serializable
data class GitBranchesResult(
    val current: String? = null,
    val branches: List<String> = emptyList(),
    val remoteBranches: List<String> = emptyList()
)

@Serializable
data class GitCheckoutResult(
    val success: Boolean = false,
    val branch: String? = null,
    val error: String? = null
)

@Serializable
data class GitPullResult(
    val success: Boolean = false,
    val updatedFiles: Int = 0,
    val error: String? = null
)

@Serializable
data class GitResetResult(
    val success: Boolean = false,
    val error: String? = null
)

@Serializable
enum class TurnGitActionKind {
    @SerialName("syncNow") SYNC_NOW,
    @SerialName("commit") COMMIT,
    @SerialName("push") PUSH,
    @SerialName("commitAndPush") COMMIT_AND_PUSH,
    @SerialName("createPR") CREATE_PR,
    @SerialName("discardRuntimeChangesAndSync") DISCARD_RUNTIME_CHANGES_AND_SYNC
}
