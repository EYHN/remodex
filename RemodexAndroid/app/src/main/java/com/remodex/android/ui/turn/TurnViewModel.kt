package com.remodex.android.ui.turn

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.remodex.android.data.model.AIFileChange
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexReviewTarget
import com.remodex.android.data.model.CodexThread
import com.remodex.android.data.model.GitBranchesWithStatusResult
import com.remodex.android.data.model.GitWorktreeChangeTransferMode
import com.remodex.android.data.model.AssistantRevertPresentation
import com.remodex.android.ui.main.ContentViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Per-screen state holder for TurnScreen, matching iOS TurnViewModel.
 *
 * Centralises the ~40 local state variables and ~10 helper functions that were
 * previously scattered as `remember { mutableStateOf(...) }` blocks inside
 * TurnScreen.  Keeping them here makes the state testable, keeps the composable
 * body slim, and mirrors the iOS architecture.
 */
class TurnViewModel(
    private val contentViewModel: ContentViewModel,
    private val scope: CoroutineScope
) {
    // ── Dialog / sheet visibility ───────────────────────────────────────
    var isOverflowMenuOpen by mutableStateOf(false)
    var isBridgeUpdateSheetOpen by mutableStateOf(false)
    var isThreadPathSheetOpen by mutableStateOf(false)
    var isCommitDialogOpen by mutableStateOf(false)
    var isRenameDialogOpen by mutableStateOf(false)
    var isDeleteDialogOpen by mutableStateOf(false)
    var isMacHandoffConfirmDialogOpen by mutableStateOf(false)
    var isRepoDiffSheetOpen by mutableStateOf(false)
    var isStatusSheetOpen by mutableStateOf(false)
    var isPlanExecutionSheetOpen by mutableStateOf(false)
    var isRuntimeActionsOpen by mutableStateOf(false)

    // ── Git branch selector ────────────────────────────────────────────
    var isBranchSelectorOpen by mutableStateOf(false)
    var isGitBranchesLoading by mutableStateOf(false)
    var isSwitchingGitBranch by mutableStateOf(false)
    var gitBranchesState by mutableStateOf<GitBranchesWithStatusResult?>(null)

    val currentGitBranchLabel: String?
        get() = gitBranchesState?.current?.trim()?.takeIf { it.isNotEmpty() }
            ?: gitBranchesState?.default?.trim()?.takeIf { it.isNotEmpty() }

    // ── Repo diff ──────────────────────────────────────────────────────
    var isRepoDiffLoading by mutableStateOf(false)
    var repoDiffPatch by mutableStateOf("")

    // ── Assistant diff / revert ────────────────────────────────────────
    var assistantDiffPatch by mutableStateOf("")
    var assistantDiffFileChanges by mutableStateOf<List<AIFileChange>>(emptyList())
    var isAssistantDiffSheetOpen by mutableStateOf(false)
    var assistantRevertSheetState by mutableStateOf<AssistantRevertSheetState?>(null)

    // ── Fork / worktree ────────────────────────────────────────────────
    var isForkingThread by mutableStateOf(false)
    var isPreparingWorktreeHandoff by mutableStateOf(false)
    var isWorktreeHandoffDialogOpen by mutableStateOf(false)
    var isCreatingWorktreeHandoff by mutableStateOf(false)
    var worktreeHandoffBranchName by mutableStateOf("")
    var worktreeHandoffBaseBranch by mutableStateOf("")
    var isPreparingForkWorktree by mutableStateOf(false)
    var isForkWorktreeDialogOpen by mutableStateOf(false)
    var isCreatingForkWorktree by mutableStateOf(false)
    var forkWorktreeBranchName by mutableStateOf("")
    var forkWorktreeBaseBranch by mutableStateOf("")

    // ── Review ─────────────────────────────────────────────────────────
    var isStartingReview by mutableStateOf(false)
    var isReviewBaseBranchDialogOpen by mutableStateOf(false)
    var reviewBaseBranchName by mutableStateOf("")

    // ── Mac handoff ────────────────────────────────────────────────────
    var isHandingOffToMac by mutableStateOf(false)

    // ── Commit dialog ──────────────────────────────────────────────────
    var commitMessage by mutableStateOf("")
    var commitAndPush by mutableStateOf(false)

    // ── Rename dialog ──────────────────────────────────────────────────
    var threadName by mutableStateOf("")

    // ── Methods ────────────────────────────────────────────────────────

    fun resetForThread(thread: CodexThread?) {
        gitBranchesState = null
        commitMessage = ""
        threadName = thread?.displayTitle.orEmpty()
        worktreeHandoffBranchName = ""
        worktreeHandoffBaseBranch = ""
        forkWorktreeBranchName = ""
        forkWorktreeBaseBranch = ""
        reviewBaseBranchName = ""
    }

    // -- Git branches --

    suspend fun loadGitBranches(
        activeThreadId: String?,
        activeThread: CodexThread?,
        showErrorToast: Boolean = false,
        context: Context? = null
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (threadId == null || workingDirectory.isNullOrBlank()) {
            gitBranchesState = null
            return
        }

        isGitBranchesLoading = true
        try {
            gitBranchesState = contentViewModel.gitBranchesWithStatus(threadId, workingDirectory)
        } catch (error: Exception) {
            gitBranchesState = null
            if (showErrorToast && context != null) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not load git branches.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } finally {
            isGitBranchesLoading = false
        }
    }

    fun refreshGitBranches(
        activeThreadId: String?,
        activeThread: CodexThread?,
        showErrorToast: Boolean = false,
        context: Context? = null
    ) {
        scope.launch {
            loadGitBranches(activeThreadId, activeThread, showErrorToast, context)
        }
    }

    fun openExistingWorktreeThread(branch: String, threads: List<CodexThread>): Boolean {
        val worktreePath = gitBranchesState?.worktreePathByBranch?.get(branch)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val normalizedWorktreePath = CodexThread.normalizeProjectPath(worktreePath) ?: return false
        val existingThreadId = threads
            .firstOrNull { it.projectKey == normalizedWorktreePath }
            ?.id ?: return false
        contentViewModel.selectThread(existingThreadId)
        return true
    }

    fun selectGitBranch(
        branch: String,
        activeThreadId: String?,
        activeThread: CodexThread?,
        threads: List<CodexThread>,
        context: Context
    ) {
        val selectedBranch = branch.trim()
        val currentBranch = gitBranchesState?.current?.trim().orEmpty()
        if (selectedBranch.isEmpty()) return
        if (selectedBranch == currentBranch) {
            isBranchSelectorOpen = false
            return
        }
        if (gitBranchesState?.branchesCheckedOutElsewhere?.contains(selectedBranch) == true) {
            if (!openExistingWorktreeThread(selectedBranch, threads)) {
                Toast.makeText(
                    context,
                    "'$selectedBranch' is already open in another worktree.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            isBranchSelectorOpen = false
            return
        }

        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (threadId == null || workingDirectory.isNullOrBlank()) return

        scope.launch {
            isSwitchingGitBranch = true
            try {
                contentViewModel.gitCheckout(threadId, selectedBranch, workingDirectory)
                loadGitBranches(activeThreadId, activeThread, showErrorToast = false)
                isBranchSelectorOpen = false
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not switch branches.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isSwitchingGitBranch = false
            }
        }
    }

    // -- Assistant diff / revert --

    fun openAssistantDiff(message: CodexMessage) {
        val changeSet = contentViewModel.diffableAIChangeSetForMessage(message) ?: return
        assistantDiffPatch = changeSet.forwardUnifiedPatch.orEmpty()
        assistantDiffFileChanges = changeSet.fileChanges
        isAssistantDiffSheetOpen = true
    }

    fun openAssistantRevert(
        message: CodexMessage,
        activeThread: CodexThread?,
        context: Context
    ) {
        val changeSet = contentViewModel.readyAIChangeSetForMessage(message) ?: return
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (workingDirectory.isNullOrBlank()) {
            Toast.makeText(context, "Undo is unavailable for this thread.", Toast.LENGTH_SHORT).show()
            return
        }

        val presentation = contentViewModel.assistantRevertPresentation(message, workingDirectory) ?: return
        assistantRevertSheetState = AssistantRevertSheetState(
            changeSetId = changeSet.id,
            workingDirectory = workingDirectory,
            presentation = presentation,
            fileChanges = changeSet.fileChanges,
            isLoadingPreview = true
        )
        scope.launch {
            try {
                val preview = contentViewModel.previewRevert(changeSet.id, workingDirectory)
                assistantRevertSheetState = assistantRevertSheetState?.copy(
                    preview = preview,
                    isLoadingPreview = false,
                    errorMessage = null
                )
            } catch (error: Exception) {
                assistantRevertSheetState = assistantRevertSheetState?.copy(
                    isLoadingPreview = false,
                    errorMessage = error.message ?: "Could not preview the revert."
                )
            }
        }
    }

    // -- Review --

    fun startReview(
        target: ComposerReviewTarget,
        activeThreadId: String?,
        activeThread: CodexThread?,
        isRunning: Boolean,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (threadId == null || workingDirectory.isNullOrBlank() || isRunning || isStartingReview) {
            if (threadId != null && workingDirectory.isNullOrBlank()) {
                Toast.makeText(context, "Code review is unavailable for this thread.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (target) {
            ComposerReviewTarget.UNCOMMITTED_CHANGES -> {
                scope.launch {
                    isStartingReview = true
                    try {
                        contentViewModel.startReview(threadId, CodexReviewTarget.UNCOMMITTED_CHANGES)
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            error.message ?: "Could not start this review.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } finally {
                        isStartingReview = false
                    }
                }
            }
            ComposerReviewTarget.BASE_BRANCH -> {
                reviewBaseBranchName = gitBranchesState?.default?.trim()?.takeIf { it.isNotEmpty() }
                    ?: currentGitBranchLabel.orEmpty()
                isReviewBaseBranchDialogOpen = true
            }
        }
    }

    fun submitBaseBranchReview(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        val normalizedBaseBranch = reviewBaseBranchName.trim()
        if (threadId == null || workingDirectory.isNullOrBlank()) return
        if (normalizedBaseBranch.isEmpty()) {
            Toast.makeText(context, "Choose a base branch before starting this review.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isStartingReview = true
            try {
                contentViewModel.startReview(threadId, CodexReviewTarget.BASE_BRANCH, normalizedBaseBranch)
                isReviewBaseBranchDialogOpen = false
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not start this review.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isStartingReview = false
            }
        }
    }

    // -- Commit --

    fun openCommitDialog(pushAfterCommit: Boolean) {
        commitAndPush = pushAfterCommit
        commitMessage = ""
        isCommitDialogOpen = true
        isOverflowMenuOpen = false
    }

    // -- Mac handoff --

    fun handOffToMac(activeThreadId: String?, context: Context) {
        val threadId = activeThreadId ?: return
        scope.launch {
            isHandingOffToMac = true
            try {
                contentViewModel.continueOnMac(threadId)
                Toast.makeText(context, "Continue on Mac requested.", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not continue this chat on your Mac.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isHandingOffToMac = false
            }
        }
    }

    // -- Rename --

    fun openRenameDialog(activeThread: CodexThread?) {
        threadName = activeThread?.displayTitle.orEmpty()
        isRenameDialogOpen = true
        isOverflowMenuOpen = false
    }

    // -- Worktree handoff --

    fun prepareWorktreeHandoff(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (threadId == null
            || workingDirectory.isNullOrBlank()
            || activeThread?.isManagedWorktreeProject == true
            || isPreparingWorktreeHandoff
            || isCreatingWorktreeHandoff
        ) {
            if (threadId != null && workingDirectory.isNullOrBlank()) {
                Toast.makeText(context, "Worktree handoff is unavailable for this thread.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        scope.launch {
            isPreparingWorktreeHandoff = true
            try {
                val branches = contentViewModel.gitBranchesWithStatus(threadId, workingDirectory)
                val preferredBaseBranch = branches.current?.trim()?.takeIf { it.isNotEmpty() }
                    ?: branches.default?.trim()?.takeIf { it.isNotEmpty() }
                    ?: branches.branches.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

                if (preferredBaseBranch == null) {
                    Toast.makeText(context, "No local base branch is available for this thread.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                worktreeHandoffBaseBranch = preferredBaseBranch
                worktreeHandoffBranchName = ""
                isRuntimeActionsOpen = false
                isWorktreeHandoffDialogOpen = true
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not load git branches.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isPreparingWorktreeHandoff = false
            }
        }
    }

    fun submitWorktreeHandoff(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        val branchName = normalizedCreatedBranchName(worktreeHandoffBranchName)
        val baseBranch = worktreeHandoffBaseBranch.trim()
        if (threadId == null || workingDirectory.isNullOrBlank() || branchName.isEmpty() || baseBranch.isEmpty()) return

        scope.launch {
            isCreatingWorktreeHandoff = true
            try {
                val result = contentViewModel.gitCreateWorktree(
                    threadId = threadId,
                    name = branchName,
                    baseBranch = baseBranch,
                    changeTransfer = GitWorktreeChangeTransferMode.MOVE,
                    cwd = workingDirectory
                )
                if (result.alreadyExisted) {
                    Toast.makeText(
                        context,
                        "A managed worktree for '$branchName' already exists. Pick a different branch name.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val worktreePath = result.worktreePath?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Could not resolve the new worktree path.")

                val movedThread = contentViewModel.moveThreadToProjectPath(threadId, worktreePath)
                val normalizedWorktreePath = CodexThread.normalizeProjectPath(worktreePath)
                    ?: throw IllegalStateException("Could not resolve the new worktree path.")
                if (movedThread.projectKey != normalizedWorktreePath) {
                    throw IllegalStateException("Could not hand off the thread to the new worktree.")
                }

                contentViewModel.selectThread(movedThread.id)
                isWorktreeHandoffDialogOpen = false
                worktreeHandoffBranchName = ""
                gitBranchesState = contentViewModel.gitBranchesWithStatus(
                    movedThread.id,
                    movedThread.gitWorkingDirectory ?: normalizedWorktreePath
                )
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Worktree handoff failed.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isCreatingWorktreeHandoff = false
            }
        }
    }

    // -- Fork worktree --

    fun prepareForkIntoNewWorktree(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        if (threadId == null || workingDirectory.isNullOrBlank() || isPreparingForkWorktree || isCreatingForkWorktree) {
            if (threadId != null && workingDirectory.isNullOrBlank()) {
                Toast.makeText(context, "Worktree fork is unavailable for this thread.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        scope.launch {
            isPreparingForkWorktree = true
            try {
                val branches = contentViewModel.gitBranchesWithStatus(threadId, workingDirectory)
                val preferredBaseBranch = branches.current?.trim()?.takeIf { it.isNotEmpty() }
                    ?: branches.default?.trim()?.takeIf { it.isNotEmpty() }
                    ?: branches.branches.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

                if (preferredBaseBranch == null) {
                    Toast.makeText(context, "No local base branch is available.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                forkWorktreeBaseBranch = preferredBaseBranch
                forkWorktreeBranchName = ""
                isForkWorktreeDialogOpen = true
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Could not load git branches.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isPreparingForkWorktree = false
            }
        }
    }

    fun submitForkIntoNewWorktree(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId
        val workingDirectory = activeThread?.gitWorkingDirectory
        val branchName = normalizedCreatedBranchName(forkWorktreeBranchName)
        val baseBranch = forkWorktreeBaseBranch.trim()
        if (threadId == null || workingDirectory.isNullOrBlank() || branchName.isEmpty() || baseBranch.isEmpty()) return

        scope.launch {
            isCreatingForkWorktree = true
            try {
                val result = contentViewModel.gitCreateWorktree(
                    threadId = threadId,
                    name = branchName,
                    baseBranch = baseBranch,
                    changeTransfer = GitWorktreeChangeTransferMode.COPY,
                    cwd = workingDirectory
                )
                if (result.alreadyExisted) {
                    Toast.makeText(
                        context,
                        "A managed worktree for '$branchName' already exists. Pick a different branch name.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val worktreePath = result.worktreePath?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Could not resolve the new worktree path.")

                try {
                    contentViewModel.forkThread(threadId, worktreePath)
                } catch (error: Exception) {
                    runCatching {
                        contentViewModel.gitRemoveManagedWorktree(worktreePath, result.branch)
                    }
                    throw error
                }

                isForkWorktreeDialogOpen = false
                forkWorktreeBranchName = ""
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Worktree fork failed.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isCreatingForkWorktree = false
            }
        }
    }

    fun startLocalFork(
        activeThreadId: String?,
        context: Context
    ) {
        val threadId = activeThreadId
        if (threadId == null || isForkingThread) return
        scope.launch {
            isForkingThread = true
            try {
                contentViewModel.forkThread(threadId)
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    error.message ?: "Fork failed.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isForkingThread = false
            }
        }
    }

    // -- Repo diff --

    fun openRepoDiff(
        activeThreadId: String?,
        activeThread: CodexThread?,
        context: Context
    ) {
        val threadId = activeThreadId ?: return
        isOverflowMenuOpen = false
        isRepoDiffSheetOpen = true
        isRepoDiffLoading = true
        repoDiffPatch = ""
        scope.launch {
            try {
                repoDiffPatch = contentViewModel.gitDiff(
                    threadId = threadId,
                    cwd = activeThread?.gitWorkingDirectory
                ).patch
            } catch (_: Exception) {
                repoDiffPatch = ""
            } finally {
                isRepoDiffLoading = false
            }
            if (repoDiffPatch.isBlank()) {
                Toast.makeText(context, "Repo diff is unavailable for this thread.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -- Revert confirm --

    fun confirmRevert(context: Context) {
        val currentState = assistantRevertSheetState ?: return
        scope.launch {
            assistantRevertSheetState = currentState.copy(isApplying = true, errorMessage = null)
            try {
                contentViewModel.applyRevert(
                    changeSetId = currentState.changeSetId,
                    workingDirectory = currentState.workingDirectory
                )
                assistantRevertSheetState = null
            } catch (error: Exception) {
                assistantRevertSheetState = currentState.copy(
                    isApplying = false,
                    errorMessage = error.message ?: "Could not undo this response."
                )
            }
        }
    }

    companion object {
        fun normalizedCreatedBranchName(rawName: String): String {
            val trimmedName = rawName.trim()
            if (trimmedName.isEmpty()) return ""
            return if (trimmedName.startsWith("remodex/")) trimmedName else "remodex/$trimmedName"
        }
    }
}

@Composable
fun rememberTurnViewModel(
    contentViewModel: ContentViewModel
): TurnViewModel {
    val scope = rememberCoroutineScope()
    return remember(contentViewModel) {
        TurnViewModel(contentViewModel, scope)
    }
}
