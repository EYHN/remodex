package com.remodex.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GitWorktreeChangeTransferMode {
    @SerialName("move") MOVE,
    @SerialName("copy") COPY
}

@Serializable
data class GitRepoSyncResult(
    val repoRoot: String? = null,
    @SerialName("branch") val currentBranch: String? = null,
    @SerialName("tracking") val trackingBranch: String? = null,
    @SerialName("dirty") val isDirty: Boolean = false,
    @SerialName("ahead") val aheadCount: Int = 0,
    @SerialName("behind") val behindCount: Int = 0,
    val localOnlyCommitCount: Int = 0,
    val state: String? = null,
    val canPush: Boolean = false,
    @SerialName("publishedToRemote") val isPublishedToRemote: Boolean = false,
    val files: List<GitChangedFile> = emptyList(),
    @SerialName("diff") val repoDiffTotals: GitDiffTotals? = null
)

@Serializable
data class GitDiffTotals(
    val additions: Int = 0,
    val deletions: Int = 0,
    val binaryFiles: Int = 0
)

@Serializable
data class GitDiffResult(
    val patch: String = ""
)

@Serializable
data class GitChangedFile(
    val path: String,
    val status: String? = null,
    val staged: Boolean = false
)

@Serializable
data class GitCommitResult(
    @SerialName("hash") val commitHash: String? = null,
    val branch: String? = null,
    @SerialName("summary") val message: String? = null
)

@Serializable
data class GitPushResult(
    val remote: String? = null,
    val branch: String? = null,
    val status: GitRepoSyncResult? = null
)

@Serializable
data class GitBranchesResult(
    val current: String? = null,
    val default: String? = null,
    val branches: List<String> = emptyList(),
    val remoteBranches: List<String> = emptyList(),
    val branchesCheckedOutElsewhere: List<String> = emptyList(),
    val worktreePathByBranch: Map<String, String> = emptyMap(),
    val localCheckoutPath: String? = null
)

@Serializable
data class GitBranchesWithStatusResult(
    val current: String? = null,
    val default: String? = null,
    val branches: List<String> = emptyList(),
    val remoteBranches: List<String> = emptyList(),
    val branchesCheckedOutElsewhere: List<String> = emptyList(),
    val worktreePathByBranch: Map<String, String> = emptyMap(),
    val localCheckoutPath: String? = null,
    val status: GitRepoSyncResult? = null
)

@Serializable
data class GitCreateWorktreeResult(
    val branch: String? = null,
    val worktreePath: String? = null,
    val alreadyExisted: Boolean = false
)

@Serializable
data class GitRemoteUrlResult(
    val url: String = "",
    val ownerRepo: String? = null
)

@Serializable
data class GitCheckoutResult(
    @SerialName("current") val branch: String? = null,
    val tracking: String? = null,
    val status: GitRepoSyncResult? = null
)

@Serializable
data class GitPullResult(
    val success: Boolean = false,
    val updatedFiles: Int = 0,
    val status: GitRepoSyncResult? = null
)

@Serializable
data class GitResetResult(
    val success: Boolean = false,
    val status: GitRepoSyncResult? = null
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
