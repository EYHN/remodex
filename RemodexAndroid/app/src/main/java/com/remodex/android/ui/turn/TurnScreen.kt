package com.remodex.android.ui.turn

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remodex.android.data.model.GitChangedFile
import com.remodex.android.data.model.GitRepoSyncResult
import com.remodex.android.data.model.AIUnifiedPatchParser
import com.remodex.android.data.model.CodexReviewTarget
import com.remodex.android.data.model.CodexThread
import com.remodex.android.service.ApprovalRequest
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.service.CodexConnectionRecoveryState
import com.remodex.android.data.model.CodexServiceTier
import com.remodex.android.ui.home.BridgeUpdateSheet
import com.remodex.android.ui.main.ContentViewModel
import com.remodex.android.ui.theme.StatusGreen
import kotlinx.coroutines.launch

@Composable
fun TurnScreen(
    viewModel: ContentViewModel,
    onOpenSidebar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onStartNewChatInProject: (String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val turnVm = rememberTurnViewModel(viewModel)
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isRunning by viewModel.isActiveThreadRunning.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()
    val connectionRecoveryState by viewModel.connectionRecoveryState.collectAsState()
    val isCurrentThreadHistoryLoading by viewModel.isCurrentThreadHistoryLoading.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModelId by viewModel.selectedModel.collectAsState()
    val selectedReasoningEffort by viewModel.selectedReasoningEffort.collectAsState()
    val selectedServiceTier by viewModel.selectedServiceTier.collectAsState()
    val selectedAccessMode by viewModel.selectedAccessMode.collectAsState()
    val supportsThreadFork by viewModel.supportsThreadFork.collectAsState()
    val contextWindowUsage by viewModel.contextWindowUsage.collectAsState()
    val rateLimitBuckets by viewModel.rateLimitBuckets.collectAsState()
    val timelineProjection = remember(messages) {
        TurnTimelineProjector.project(messages)
    }

    val activeThread = remember(activeThreadId, threads) {
        threads.find { it.id == activeThreadId }
    }
    val activeThreadPath = remember(activeThread) {
        activeThread?.projectKey ?: activeThread?.cwd?.trim()?.takeIf { it.isNotBlank() }
    }
    val activeThreadSubtitle = remember(activeThread, activeThreadPath) {
        activeThreadPath ?: activeThread?.projectDisplayName
    }
    val selectedModelTitle = remember(availableModels, selectedModelId) {
        availableModels.firstOrNull { model ->
            model.id == selectedModelId || model.model == selectedModelId
        }?.label ?: "Auto"
    }
    val supportedReasoningEfforts = remember(availableModels, selectedModelId) {
        viewModel.supportedReasoningEffortsForSelectedModel()
    }
    val turnConnectionBannerMessage = remember(connectionPhase, connectionRecoveryState) {
        when {
            connectionRecoveryState is CodexConnectionRecoveryState.Retrying ->
                (connectionRecoveryState as CodexConnectionRecoveryState.Retrying).message
            connectionPhase == CodexConnectionPhase.CONNECTING -> "Connecting to your bridge..."
            connectionPhase == CodexConnectionPhase.HANDSHAKING -> "Securing local connection..."
            connectionPhase == CodexConnectionPhase.LOADING_CHATS -> "Loading chats..."
            connectionPhase == CodexConnectionPhase.SYNCING -> "Syncing workspace..."
            else -> null
        }
    }
    val showTurnConnectionBanner = activeThreadId != null
        && connectionPhase != CodexConnectionPhase.CONNECTED
        && connectionPhase != CodexConnectionPhase.OFFLINE
        && !turnConnectionBannerMessage.isNullOrBlank()
    var isComposerFocused by remember(activeThreadId) { mutableStateOf(false) }
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val shouldHideComposerStatusBar = isComposerFocused && isImeVisible

    // State is managed by TurnViewModel (turnVm) — see TurnViewModel.kt

    LaunchedEffect(activeThreadId) {
        turnVm.resetForThread(activeThread)
    }

    // Local delegation helpers that capture the current thread context for turnVm methods.

    fun refreshGitBranches(showErrorToast: Boolean = false) {
        turnVm.refreshGitBranches(activeThreadId, activeThread, showErrorToast, context)
    }

    fun startReview(target: ComposerReviewTarget) {
        turnVm.startReview(target, activeThreadId, activeThread, isRunning, context)
    }

    LaunchedEffect(activeThreadId) {
        activeThreadId?.let { threadId ->
            viewModel.refreshActiveThreadNow(threadId)
            viewModel.refreshContextWindowUsage(threadId)
            viewModel.refreshRateLimits()
        }
    }

    LaunchedEffect(activeThreadId, activeThread?.gitWorkingDirectory) {
        turnVm.loadGitBranches(activeThreadId, activeThread, showErrorToast = false)
    }

    LaunchedEffect(timelineProjection.pinnedPlanMessage?.id) {
        if (timelineProjection.pinnedPlanMessage == null) {
            turnVm.isPlanExecutionSheetOpen = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TurnTopBar(
                title = activeThread?.displayTitle ?: "Chat",
                subtitle = activeThreadSubtitle,
                onOpenSidebar = onOpenSidebar,
                onSubtitleClick = if (activeThreadPath != null) { { turnVm.isThreadPathSheetOpen = true } } else null,
                onOpenMenu = { turnVm.isOverflowMenuOpen = true },
                onOpenStatus = { turnVm.isStatusSheetOpen = true }
            )

            if (showTurnConnectionBanner) {
                TurnConnectionBanner(
                    text = turnConnectionBannerMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            pendingApproval?.let { approval ->
                ApprovalBanner(
                    approval = approval,
                    onApprove = { viewModel.respondToApproval(true) },
                    onReject = { viewModel.respondToApproval(false) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            TurnTimeline(
                threadId = activeThreadId,
                messages = timelineProjection.messages,
                isRunning = isRunning,
                isHistoryLoading = isCurrentThreadHistoryLoading && timelineProjection.messages.isEmpty(),
                suppressEmptyState = timelineProjection.pinnedPlanMessage != null && timelineProjection.messages.isEmpty(),
                assistantRevertPresentationForMessage = { message ->
                    viewModel.assistantRevertPresentation(message, activeThread?.gitWorkingDirectory)
                },
                assistantDiffAvailableForMessage = { message ->
                    viewModel.diffableAIChangeSetForMessage(message) != null
                },
                onOpenAssistantDiff = turnVm::openAssistantDiff,
                onOpenAssistantRevert = { msg -> turnVm.openAssistantRevert(msg, activeThread, context) },
                onSubmitStructuredUserInput = viewModel::respondToStructuredUserInput,
                onOpenSubagentThread = { threadId -> viewModel.selectThread(threadId) },
                onRetryUserMessage = { text, attachments -> viewModel.sendMessage(text, attachments) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            timelineProjection.pinnedPlanMessage?.let { planMessage ->
                PlanExecutionAccessory(
                    message = planMessage,
                    onClick = { turnVm.isPlanExecutionSheetOpen = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            TurnComposer(
                draftKey = activeThreadId,
                isRunning = isRunning,
                isConnected = isConnected,
                autocompleteRoot = activeThreadPath,
                availableModels = availableModels,
                selectedModelId = selectedModelId,
                selectedModelTitle = selectedModelTitle,
                selectedReasoningEffort = selectedReasoningEffort,
                selectedServiceTier = selectedServiceTier,
                supportedReasoningEfforts = supportedReasoningEfforts,
                onSelectModel = viewModel::setSelectedModel,
                onSelectReasoningEffort = viewModel::setSelectedReasoningEffort,
                onSelectServiceTier = viewModel::setSelectedServiceTier,
                onFuzzyFileSearch = viewModel::fuzzyFileSearch,
                onListSkills = viewModel::listSkills,
                supportsReviewCommand = activeThreadId != null
                    && !activeThread?.gitWorkingDirectory.isNullOrBlank()
                    && !isRunning,
                supportsForkCommand = supportsThreadFork && activeThreadId != null && !isRunning,
                onReviewAction = ::startReview,
                onForkAction = { destination ->
                    when (destination) {
                        ComposerForkDestination.LOCAL -> turnVm.startLocalFork(activeThreadId, context)
                        ComposerForkDestination.NEW_WORKTREE -> turnVm.prepareForkIntoNewWorktree(activeThreadId, activeThread, context)
                    }
                },
                onShowStatus = { turnVm.isStatusSheetOpen = true },
                onSteerQueuedDraft = { text, attachments, skillMentions, collaborationMode ->
                    val threadId = activeThreadId ?: throw IllegalStateException("No active thread.")
                    viewModel.steerTurn(
                        text = text,
                        threadId = threadId,
                        attachments = attachments,
                        skillMentions = skillMentions,
                        collaborationMode = collaborationMode
                    )
                },
                onSend = { text, attachments, skillMentions, collaborationMode ->
                    viewModel.sendMessage(text, attachments, skillMentions, collaborationMode)
                },
                onTranscribeVoiceClip = viewModel::transcribeVoiceClip,
                onStop = { viewModel.stopTurn() },
                onInputFocusChanged = { isComposerFocused = it },
                modifier = Modifier.fillMaxWidth()
            )

            TurnComposerStatusBar(
                isComposerFocused = shouldHideComposerStatusBar,
                isWorktreeProject = activeThread?.isManagedWorktreeProject == true,
                isRuntimeSelectorEnabled = activeThreadId != null,
                onOpenRuntimeActions = { turnVm.isRuntimeActionsOpen = true },
                selectedAccessMode = selectedAccessMode,
                onSelectAccessMode = viewModel::setAccessMode,
                currentBranch = turnVm.currentGitBranchLabel,
                isBranchSelectorEnabled = !isRunning && !turnVm.isGitBranchesLoading && !turnVm.isSwitchingGitBranch,
                onOpenBranchSelector = { turnVm.isBranchSelectorOpen = true },
                contextWindowUsage = contextWindowUsage,
                onOpenStatusSheet = { turnVm.isStatusSheetOpen = true }
            )
        }

        if (turnVm.isStatusSheetOpen) {
            TurnStatusSheet(
                contextWindowUsage = contextWindowUsage,
                rateLimitBuckets = rateLimitBuckets,
                onDismiss = { turnVm.isStatusSheetOpen = false }
            )
        }

        timelineProjection.pinnedPlanMessage?.takeIf { turnVm.isPlanExecutionSheetOpen }?.let { planMessage ->
            PlanExecutionSheet(
                message = planMessage,
                onDismiss = { turnVm.isPlanExecutionSheetOpen = false }
            )
        }

        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 56.dp, end = 12.dp)
                .align(Alignment.TopEnd)
        ) {
            DropdownMenu(
                expanded = turnVm.isOverflowMenuOpen,
                onDismissRequest = { turnVm.isOverflowMenuOpen = false },
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                MenuSectionHeader("Chat")
                MenuActionItem(
                    label = if (turnVm.isHandingOffToMac) "Handing off to Mac..." else "Hand off to Mac",
                    icon = Icons.Default.LaptopMac,
                    enabled = activeThreadId != null && !turnVm.isHandingOffToMac,
                    onClick = {
                        turnVm.isOverflowMenuOpen = false
                        turnVm.isMacHandoffConfirmDialogOpen = true
                    }
                )
                MenuActionItem(
                    label = "New chat",
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    onClick = {
                        onStartNewChatInProject(activeThread?.projectKey ?: activeThread?.cwd)
                        turnVm.isOverflowMenuOpen = false
                    }
                )
                MenuActionItem(
                    label = if (turnVm.isForkingThread) "Forking..." else "Fork",
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    enabled = activeThreadId != null && !turnVm.isForkingThread,
                    onClick = {
                        turnVm.isOverflowMenuOpen = false
                        turnVm.startLocalFork(activeThreadId, context)
                    }
                )
                MenuActionItem(
                    label = if (turnVm.isPreparingForkWorktree || turnVm.isCreatingForkWorktree) {
                        "Preparing worktree fork..."
                    } else {
                        "Fork into new worktree"
                    },
                    icon = Icons.Default.AccountTree,
                    enabled = activeThreadId != null && !turnVm.isPreparingForkWorktree && !turnVm.isCreatingForkWorktree,
                    onClick = {
                        turnVm.isOverflowMenuOpen = false
                        turnVm.prepareForkIntoNewWorktree(activeThreadId, activeThread, context)
                    }
                )
                MenuActionItem(
                    label = "Rename",
                    icon = Icons.Default.Edit,
                    enabled = activeThreadId != null,
                    onClick = { turnVm.openRenameDialog(activeThread) }
                )
                MenuActionItem(
                    label = "Archive",
                    icon = Icons.Default.Archive,
                    enabled = activeThreadId != null,
                    onClick = {
                        activeThreadId?.let(viewModel::archiveThread)
                        turnVm.isOverflowMenuOpen = false
                    }
                )
                MenuActionItem(
                    label = "Delete",
                    icon = Icons.Default.DeleteOutline,
                    enabled = activeThreadId != null,
                    onClick = {
                        turnVm.isDeleteDialogOpen = true
                        turnVm.isOverflowMenuOpen = false
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                MenuSectionHeader("Update")
                MenuActionItem(
                    label = "Update bridge",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        turnVm.isBridgeUpdateSheetOpen = true
                        turnVm.isOverflowMenuOpen = false
                    }
                )
                MenuActionItem(
                    label = "Scan QR code",
                    icon = Icons.Default.Download,
                    onClick = {
                        onNavigateToScanner()
                        turnVm.isOverflowMenuOpen = false
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                MenuSectionHeader("Write")
                MenuActionItem(
                    label = "Commit",
                    icon = Icons.Default.PostAdd,
                    enabled = activeThreadId != null,
                    onClick = {
                        turnVm.openCommitDialog(pushAfterCommit = false)
                    }
                )
                MenuActionItem(
                    label = "Push",
                    icon = Icons.Default.Upload,
                    enabled = activeThreadId != null,
                    onClick = {
                        activeThreadId?.let { viewModel.gitPush(it) }
                        turnVm.isOverflowMenuOpen = false
                    }
                )
                MenuActionItem(
                    label = "Commit & Push",
                    icon = Icons.Default.Sync,
                    enabled = activeThreadId != null,
                    onClick = {
                        turnVm.openCommitDialog(pushAfterCommit = true)
                    }
                )
                MenuActionItem(
                    label = "Create PR",
                    icon = Icons.Default.MoreVert,
                    enabled = !activeThread?.cwd.isNullOrBlank(),
                    onClick = {
                        val workingDirectory = activeThread?.cwd
                        if (workingDirectory.isNullOrBlank()) {
                            Toast.makeText(context, "Pull request is unavailable for this thread.", Toast.LENGTH_SHORT).show()
                            turnVm.isOverflowMenuOpen = false
                        } else {
                            coroutineScope.launch {
                                val url = viewModel.createPullRequestUrl(
                                    cwd = workingDirectory,
                                    currentBranchHint = null
                                )
                                if (url.isNullOrBlank()) {
                                    Toast.makeText(
                                        context,
                                        "Pull request is unavailable for the current branch.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            turnVm.isOverflowMenuOpen = false
                        }
                    }
                )
                MenuActionItem(
                    label = "Repo diff",
                    icon = Icons.Default.Refresh,
                    enabled = activeThreadId != null,
                    onClick = { turnVm.openRepoDiff(activeThreadId, activeThread, context) }
                )
                MenuActionItem(
                    label = "Pull",
                    icon = Icons.Default.Download,
                    enabled = activeThreadId != null,
                    onClick = {
                        activeThreadId?.let { viewModel.gitPull(it) }
                        turnVm.isOverflowMenuOpen = false
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                MenuActionItem(
                    label = "Settings",
                    icon = Icons.Default.Settings,
                    onClick = {
                        onNavigateToSettings()
                        turnVm.isOverflowMenuOpen = false
                    }
                )
            }
        }

        if (turnVm.isBridgeUpdateSheetOpen) {
            BridgeUpdateSheet(
                onDismiss = { turnVm.isBridgeUpdateSheetOpen = false },
                onUpdated = { turnVm.isBridgeUpdateSheetOpen = false },
                onScanNewQR = {
                    turnVm.isBridgeUpdateSheetOpen = false
                    onNavigateToScanner()
                }
            )
        }

        if (turnVm.isThreadPathSheetOpen && activeThreadPath != null) {
            ThreadPathSheet(
                threadTitle = activeThread?.displayTitle ?: "Chat",
                fullPath = activeThreadPath,
                onDismiss = { turnVm.isThreadPathSheetOpen = false },
                onRenameThread = {
                    turnVm.isThreadPathSheetOpen = false
                    turnVm.openRenameDialog(activeThread)
                }
            )
        }

        if (turnVm.isRepoDiffSheetOpen) {
            if (turnVm.isRepoDiffLoading) {
                RepoDiffSheet(
                    status = null,
                    isLoading = true,
                    onDismiss = { turnVm.isRepoDiffSheetOpen = false }
                )
            } else {
                val repoDiffAnalysis = remember(turnVm.repoDiffPatch) { AIUnifiedPatchParser.analyze(turnVm.repoDiffPatch) }
                TurnDiffSheet(
                    title = "Repo diff",
                    subtitle = activeThread?.gitWorkingDirectory,
                    patch = turnVm.repoDiffPatch,
                    fileChanges = repoDiffAnalysis.fileChanges,
                    onDismiss = { turnVm.isRepoDiffSheetOpen = false }
                )
            }
        }

        if (turnVm.isAssistantDiffSheetOpen) {
            TurnDiffSheet(
                title = "Response diff",
                subtitle = activeThread?.gitWorkingDirectory,
                patch = turnVm.assistantDiffPatch,
                fileChanges = turnVm.assistantDiffFileChanges,
                onDismiss = { turnVm.isAssistantDiffSheetOpen = false }
            )
        }

        turnVm.assistantRevertSheetState?.let { revertState ->
            AssistantRevertSheet(
                state = revertState,
                onDismiss = { turnVm.assistantRevertSheetState = null },
                onConfirm = { turnVm.confirmRevert(context) }
            )
        }

        if (turnVm.isBranchSelectorOpen) {
            BranchSelectorSheet(
                currentBranch = turnVm.gitBranchesState?.current,
                defaultBranch = turnVm.gitBranchesState?.default,
                branches = turnVm.gitBranchesState?.branches.orEmpty(),
                branchesCheckedOutElsewhere = turnVm.gitBranchesState?.branchesCheckedOutElsewhere.orEmpty().toSet(),
                worktreePathByBranch = turnVm.gitBranchesState?.worktreePathByBranch.orEmpty(),
                isLoading = turnVm.isGitBranchesLoading,
                isSwitching = turnVm.isSwitchingGitBranch,
                onDismiss = { turnVm.isBranchSelectorOpen = false },
                onRefresh = { refreshGitBranches(showErrorToast = true) },
                onSelectBranch = { branch -> turnVm.selectGitBranch(branch, activeThreadId, activeThread, threads, context) }
            )
        }

        if (turnVm.isReviewBaseBranchDialogOpen) {
            AlertDialog(
                onDismissRequest = {
                    if (!turnVm.isStartingReview) {
                        turnVm.isReviewBaseBranchDialogOpen = false
                    }
                },
                title = { Text("Review against base branch") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Choose the base branch the reviewer should compare against.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = turnVm.reviewBaseBranchName,
                            onValueChange = { turnVm.reviewBaseBranchName = it },
                            singleLine = true,
                            label = { Text("Base branch") },
                            placeholder = { Text(turnVm.gitBranchesState?.default ?: "main") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { turnVm.submitBaseBranchReview(activeThreadId, activeThread, context) },
                        enabled = !turnVm.isStartingReview
                    ) {
                        Text(if (turnVm.isStartingReview) "Starting..." else "Start review")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { turnVm.isReviewBaseBranchDialogOpen = false },
                        enabled = !turnVm.isStartingReview
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (turnVm.isRuntimeActionsOpen) {
            RuntimeActionsSheet(
                isWorktreeProject = activeThread?.isManagedWorktreeProject == true,
                canHandOffToWorktree = activeThreadId != null
                    && !isRunning
                    && !activeThread?.gitWorkingDirectory.isNullOrBlank()
                    && activeThread?.isManagedWorktreeProject != true
                    && !turnVm.isPreparingWorktreeHandoff
                    && !turnVm.isCreatingWorktreeHandoff,
                isPreparingWorktreeHandoff = turnVm.isPreparingWorktreeHandoff || turnVm.isCreatingWorktreeHandoff,
                onDismiss = { turnVm.isRuntimeActionsOpen = false },
                onSelectWorktree = { turnVm.prepareWorktreeHandoff(activeThreadId, activeThread, context) }
            )
        }

        if (turnVm.isWorktreeHandoffDialogOpen) {
            WorktreeBranchDialog(
                mode = WorktreeBranchDialogMode.HANDOFF,
                branchName = turnVm.worktreeHandoffBranchName,
                baseBranch = turnVm.worktreeHandoffBaseBranch,
                isSubmitting = turnVm.isCreatingWorktreeHandoff,
                onBranchNameChange = { turnVm.worktreeHandoffBranchName = it },
                onDismiss = {
                    if (!turnVm.isCreatingWorktreeHandoff) {
                        turnVm.isWorktreeHandoffDialogOpen = false
                    }
                },
                onConfirm = { turnVm.submitWorktreeHandoff(activeThreadId, activeThread, context) }
            )
        }

        if (turnVm.isForkWorktreeDialogOpen) {
            WorktreeBranchDialog(
                mode = WorktreeBranchDialogMode.FORK,
                branchName = turnVm.forkWorktreeBranchName,
                baseBranch = turnVm.forkWorktreeBaseBranch,
                isSubmitting = turnVm.isCreatingForkWorktree,
                onBranchNameChange = { turnVm.forkWorktreeBranchName = it },
                onDismiss = {
                    if (!turnVm.isCreatingForkWorktree) {
                        turnVm.isForkWorktreeDialogOpen = false
                    }
                },
                onConfirm = { turnVm.submitForkIntoNewWorktree(activeThreadId, activeThread, context) }
            )
        }

        if (turnVm.isCommitDialogOpen) {
            CommitMessageDialog(
                value = turnVm.commitMessage,
                onValueChange = { turnVm.commitMessage = it },
                onDismiss = {
                    turnVm.isCommitDialogOpen = false
                    turnVm.commitMessage = ""
                },
                onConfirm = {
                    val threadId = activeThreadId
                    if (threadId != null) {
                        viewModel.gitCommit(threadId, turnVm.commitMessage.trim())
                        if (turnVm.commitAndPush) {
                            viewModel.gitPush(threadId)
                        }
                    }
                    turnVm.isCommitDialogOpen = false
                    turnVm.commitMessage = ""
                },
                confirmLabel = if (turnVm.commitAndPush) "Commit & Push" else "Commit"
            )
        }

        if (turnVm.isRenameDialogOpen) {
            ThreadRenameDialog(
                value = turnVm.threadName,
                onValueChange = { turnVm.threadName = it },
                onDismiss = { turnVm.isRenameDialogOpen = false },
                onConfirm = {
                    activeThreadId?.let { viewModel.renameThread(it, turnVm.threadName.trim()) }
                    turnVm.isRenameDialogOpen = false
                }
            )
        }

        if (turnVm.isDeleteDialogOpen) {
            AlertDialog(
                onDismissRequest = { turnVm.isDeleteDialogOpen = false },
                title = { Text("Delete chat") },
                text = { Text("Delete \"${activeThread?.displayTitle ?: "this chat"}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            activeThreadId?.let(viewModel::deleteThread)
                            turnVm.isDeleteDialogOpen = false
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { turnVm.isDeleteDialogOpen = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (turnVm.isMacHandoffConfirmDialogOpen) {
            AlertDialog(
                onDismissRequest = { turnVm.isMacHandoffConfirmDialogOpen = false },
                title = { Text("Hand off to Mac app") },
                text = {
                    Text(
                        "Remodex will force close and reopen Codex.app on your Mac. " +
                            "Any desktop runs in progress will be stopped, and unsaved draft text " +
                            "there may be lost before this chat is opened."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            turnVm.isMacHandoffConfirmDialogOpen = false
                            turnVm.handOffToMac(activeThreadId, context)
                        },
                        enabled = !turnVm.isHandingOffToMac
                    ) {
                        Text("Force Close & Continue")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { turnVm.isMacHandoffConfirmDialogOpen = false },
                        enabled = !turnVm.isHandingOffToMac
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeActionsSheet(
    isWorktreeProject: Boolean,
    canHandOffToWorktree: Boolean,
    isPreparingWorktreeHandoff: Boolean,
    onDismiss: () -> Unit,
    onSelectWorktree: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Continue in",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = if (isWorktreeProject) {
                    "This chat is already running inside a managed worktree."
                } else {
                    "Move the current local chat into a managed worktree without changing its thread id."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RuntimeSurfaceRow(
                icon = Icons.Default.Download,
                title = "Cloud",
                subtitle = "Local-first Android builds keep cloud handoff disabled.",
                enabled = false,
                onClick = {}
            )

            RuntimeSurfaceRow(
                icon = Icons.Default.AccountTree,
                title = if (isPreparingWorktreeHandoff) {
                    "Preparing worktree..."
                } else if (isWorktreeProject) {
                    "Already in worktree"
                } else {
                    "Hand off to worktree"
                },
                subtitle = if (isWorktreeProject) {
                    "Managed worktree threads stay bound to their current checkout."
                } else {
                    "Create a new branch and move tracked local changes into that worktree."
                },
                enabled = canHandOffToWorktree,
                onClick = onSelectWorktree
            )

            RuntimeSurfaceRow(
                icon = Icons.Default.LaptopMac,
                title = "Local",
                subtitle = "Returning a managed thread back to local is not available yet.",
                enabled = false,
                onClick = {}
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RuntimeSurfaceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchSelectorSheet(
    currentBranch: String?,
    defaultBranch: String?,
    branches: List<String>,
    branchesCheckedOutElsewhere: Set<String>,
    worktreePathByBranch: Map<String, String>,
    isLoading: Boolean,
    isSwitching: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBranch: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Current branch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            currentBranch?.trim()?.takeIf { it.isNotEmpty() }?.let { branch ->
                Text(
                    text = branch,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            defaultBranch?.trim()?.takeIf { it.isNotEmpty() }?.let { branch ->
                Text(
                    text = "Default branch: $branch",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                isLoading -> {
                    Text(
                        text = "Loading branches...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                branches.isEmpty() -> {
                    Text(
                        text = "No local branches are available for this thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    branches.forEach { branch ->
                        val normalizedBranch = branch.trim()
                        val isCurrent = normalizedBranch == currentBranch?.trim()
                        val isCheckedOutElsewhere = branchesCheckedOutElsewhere.contains(normalizedBranch)
                        val worktreePath = worktreePathByBranch[normalizedBranch]
                        val isDisabled = isSwitching
                            || (isCheckedOutElsewhere && worktreePath.isNullOrBlank())

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(enabled = !isDisabled) {
                                    onSelectBranch(normalizedBranch)
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = normalizedBranch,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    val subtitle = when {
                                        isCurrent -> "Checked out here"
                                        isCheckedOutElsewhere && !worktreePath.isNullOrBlank() -> "Open existing worktree"
                                        isCheckedOutElsewhere -> "Checked out in another worktree"
                                        else -> null
                                    }
                                    subtitle?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isSwitching
                ) {
                    Text("Refresh")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoDiffSheet(
    status: GitRepoSyncResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Repo diff",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            when {
                isLoading -> {
                    Text(
                        text = "Loading repo status...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                status == null -> {
                    Text(
                        text = "Git status is unavailable for this thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    GitRepoSummarySection(status = status)

                    if (status.files.isNotEmpty()) {
                        Text(
                            text = "Changed files",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        status.files.forEach { file ->
                            GitChangedFileRow(file = file)
                        }
                    } else {
                        Text(
                            text = "No modified files reported for this thread.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private enum class WorktreeBranchDialogMode(
    val title: String,
    val message: String,
    val confirmLabel: String
) {
    HANDOFF(
        title = "Hand off thread to worktree",
        message = "Create and check out a branch in a new worktree, then move this conversation into that checkout. Tracked local changes move there too, while ignored files stay in Local.",
        confirmLabel = "Hand off"
    ),
    FORK(
        title = "Fork thread into new worktree",
        message = "Create and check out a branch in a new worktree, then fork this conversation into the new checkout. Tracked local changes are copied there too, while the current thread stays where it is.",
        confirmLabel = "Fork"
    )
}


@Composable
private fun WorktreeBranchDialog(
    mode: WorktreeBranchDialogMode,
    branchName: String,
    baseBranch: String,
    isSubmitting: Boolean,
    onBranchNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(mode.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = mode.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Base branch: $baseBranch",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = onBranchNameChange,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Branch name") },
                    placeholder = { Text("feature-name") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = TurnViewModel.normalizedCreatedBranchName(branchName).isNotEmpty()
                    && baseBranch.isNotBlank()
                    && !isSubmitting
            ) {
                Text(
                    if (isSubmitting) {
                        when (mode) {
                            WorktreeBranchDialogMode.HANDOFF -> "Handing off..."
                            WorktreeBranchDialogMode.FORK -> "Forking..."
                        }
                    } else {
                        mode.confirmLabel
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurnTopBar(
    title: String,
    subtitle: String?,
    onOpenSidebar: () -> Unit,
    onSubtitleClick: (() -> Unit)?,
    onOpenMenu: () -> Unit,
    onOpenStatus: () -> Unit
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarChipButton(
                icon = Icons.Default.Menu,
                contentDescription = "Open sidebar",
                onClick = onOpenSidebar
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        modifier = if (onSubtitleClick != null) {
                            Modifier.clickable(onClick = onSubtitleClick)
                        } else {
                            Modifier
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopBarChipButton(
                    icon = Icons.Default.ArrowUpward,
                    contentDescription = "Thread actions",
                    onClick = onOpenMenu
                )

                TopBarChipButton(
                    icon = Icons.Default.Tune,
                    contentDescription = "Open status",
                    onClick = onOpenStatus
                )
            }
        }
    }
}

@Composable
private fun GitRepoSummarySection(status: GitRepoSyncResult) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RepoSummaryRow(
                label = "Branch",
                value = status.currentBranch?.takeIf { it.isNotBlank() } ?: "Unknown"
            )
            status.trackingBranch?.takeIf { it.isNotBlank() }?.let { tracking ->
                RepoSummaryRow(label = "Tracking", value = tracking)
            }
            RepoSummaryRow(
                label = "Workspace",
                value = if (status.isDirty) "Dirty" else "Clean"
            )
            RepoSummaryRow(
                label = "Ahead / Behind",
                value = "${status.aheadCount} / ${status.behindCount}"
            )
            status.repoDiffTotals?.let { totals ->
                RepoSummaryRow(
                    label = "Diff totals",
                    value = "+${totals.additions}  -${totals.deletions}"
                )
            }
            status.repoRoot?.takeIf { it.isNotBlank() }?.let { repoRoot ->
                RepoSummaryRow(label = "Repo root", value = repoRoot)
            }
        }
    }
}

@Composable
private fun RepoSummaryRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GitChangedFileRow(file: GitChangedFile) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            val statusLabel = buildString {
                append(file.status?.takeIf { it.isNotBlank() } ?: "modified")
                if (file.staged) {
                    append(" • staged")
                }
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPathSheet(
    threadTitle: String,
    fullPath: String,
    onDismiss: () -> Unit,
    onRenameThread: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Thread path",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = threadTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Full path",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = fullPath,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onRenameThread,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rename thread")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TopBarChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    // iOS-style: plain icon button, no circular background
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MenuSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun MenuActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null)
        },
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ApprovalBanner(
    approval: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Approval required",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${approval.toolName}: ${approval.description}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Allow")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deny")
                }
            }
        }
    }
}

@Composable
private fun TurnConnectionBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommitMessageDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(confirmLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter a commit message for the current thread.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Describe the change") },
                    singleLine = false,
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.trim().isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThreadRenameDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Conversation title") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = value.trim().isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
