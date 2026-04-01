package com.remodex.android.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remodex.android.data.model.*
import com.remodex.android.data.store.SecureStore
import com.remodex.android.service.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import android.os.Build
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val codexService: CodexService,
    private val secureStore: SecureStore,
    private val secureTransport: SecureTransport,
    private val runCompletionNotifier: RunCompletionNotifier,
    private val notificationIntentRouter: NotificationIntentRouter,
    private val json: Json
) : ViewModel() {

    // Expose service state
    val threads = codexService.threads
    val activeThreadId = codexService.activeThreadId
    val messagesByThread = codexService.messagesByThread
    val isConnected = codexService.isConnected
    val isConnecting = codexService.isConnecting
    val connectionPhase = codexService.connectionPhase
    val connectionRecoveryState = codexService.connectionRecoveryState
    val secureConnectionState = codexService.secureConnectionState
    val connectionErrorMessage = secureTransport.lastErrorMessage
    val isBootstrappingConnectionSync = codexService.isBootstrappingConnectionSync
    val isLoadingThreads = codexService.isLoadingThreads
    val isLoadingModels = codexService.isLoadingModels
    val loadingThreadIds = codexService.loadingThreadIds
    val runningThreadIDs = codexService.runningThreadIDs
    val readyThreadIDs = codexService.readyThreadIDs
    val failedThreadIDs = codexService.failedThreadIDs
    val pendingApproval = codexService.pendingApproval
    val availableModels = codexService.availableModels
    val selectedModel = codexService.selectedModel
    val selectedReasoningEffort = codexService.selectedReasoningEffort
    val selectedServiceTier = codexService.selectedServiceTier
    val selectedAccessMode = codexService.selectedAccessMode
    val supportsThreadFork = codexService.supportsThreadFork
    val contextWindowUsage = codexService.contextWindowUsage
    val rateLimitBuckets = codexService.rateLimitBuckets
    val bridgeVersionInfo = codexService.bridgeVersionInfo
    val gptAccountSnapshot = codexService.gptAccountSnapshot
    val gptAccountErrorMessage = codexService.gptAccountErrorMessage
    val aiChangeSetRevision = codexService.aiChangeSetRevision
    val notificationsEnabled = runCompletionNotifier.notificationsEnabled
    val notificationPermissionGranted = runCompletionNotifier.notificationPermissionGranted
    val notificationPermissionPrompted = runCompletionNotifier.permissionPrompted
    val missingNotificationThreadPrompt = codexService.missingNotificationThreadPrompt
    private val _trustedPairPresentation = MutableStateFlow(codexService.trustedPairPresentation())
    val trustedPairPresentation: StateFlow<CodexTrustedPairPresentation?> = _trustedPairPresentation.asStateFlow()
    private val _shouldPromptForNotificationPermission = MutableStateFlow(false)
    val shouldPromptForNotificationPermission: StateFlow<Boolean> =
        _shouldPromptForNotificationPermission.asStateFlow()
    private val _notificationNavigationToken = MutableStateFlow(0L)
    val notificationNavigationToken: StateFlow<Long> =
        _notificationNavigationToken.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow(
        secureStore.readString(SecureStore.HAS_SEEN_ONBOARDING) == "true"
    )
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val currentMessages: StateFlow<List<CodexMessage>> = combine(
        activeThreadId, messagesByThread
    ) { threadId, messages ->
        threadId?.let { messages[it] } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isActiveThreadRunning: StateFlow<Boolean> = combine(
        activeThreadId, runningThreadIDs
    ) { threadId, running ->
        threadId != null && threadId in running
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCurrentThreadHistoryLoading: StateFlow<Boolean> = combine(
        activeThreadId,
        codexService.loadingThreadIds
    ) { threadId, loadingThreadIds ->
        threadId != null && threadId in loadingThreadIds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Auto-reconnect if saved relay exists
        if (codexService.hasSavedRelay() && _hasSeenOnboarding.value) {
            codexService.connectFromSavedRelay()
        }

        viewModelScope.launch {
            combine(
                codexService.connectionPhase,
                codexService.isConnected,
                secureTransport.state
            ) { _, _, _ ->
                codexService.trustedPairPresentation()
            }.collect { presentation ->
                _trustedPairPresentation.value = presentation
            }
        }

        viewModelScope.launch {
            codexService.isConnected.collect {
                refreshNotificationPermissionState()
            }
        }

        viewModelScope.launch {
            combine(
                codexService.isConnected,
                runCompletionNotifier.notificationPermissionGranted,
                runCompletionNotifier.permissionPrompted
            ) { connected, granted, prompted ->
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && connected && !granted && !prompted
            }.collect { shouldPrompt ->
                _shouldPromptForNotificationPermission.value = shouldPrompt
            }
        }

        viewModelScope.launch {
            notificationIntentRouter.pendingOpenTarget.collect { target ->
                target ?: return@collect
                codexService.handleNotificationOpen(target.threadId, target.turnId)
                _notificationNavigationToken.value = _notificationNavigationToken.value + 1L
                notificationIntentRouter.clearPendingOpenTarget()
            }
        }
    }

    fun markOnboardingSeen() {
        secureStore.writeString(SecureStore.HAS_SEEN_ONBOARDING, "true")
        _hasSeenOnboarding.value = true
    }

    fun toggleSidebar() {
        _isSidebarOpen.value = !_isSidebarOpen.value
    }

    fun openSidebar() { _isSidebarOpen.value = true }
    fun closeSidebar() { _isSidebarOpen.value = false }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun selectThread(threadId: String?) {
        codexService.selectThread(threadId)
        _isSidebarOpen.value = false
    }

    fun startNewThread(projectPath: String? = null) {
        codexService.startNewThread(projectPath)
        _isSidebarOpen.value = false
    }

    fun renameThread(threadId: String, name: String) {
        codexService.renameThread(threadId, name)
    }

    fun archiveThread(threadId: String) {
        codexService.archiveThread(threadId)
    }

    fun unarchiveThread(threadId: String) {
        codexService.unarchiveThread(threadId)
    }

    fun deleteThread(threadId: String) {
        codexService.deleteThread(threadId)
    }

    fun refreshNotificationPermissionState() {
        runCompletionNotifier.refreshState()
        refreshNotificationPromptState()
    }

    fun markNotificationPermissionPrompted() {
        runCompletionNotifier.markPermissionPrompted()
        refreshNotificationPromptState()
    }

    fun dismissMissingNotificationThreadPrompt() {
        codexService.dismissMissingNotificationThreadPrompt()
    }

    suspend fun retryPendingNotificationRoute(): Boolean =
        codexService.routePendingNotificationOpenIfPossible(refreshIfNeeded = true)

    fun sendMessage(
        text: String,
        attachments: List<CodexImageAttachment> = emptyList(),
        skillMentions: List<CodexTurnSkillMention> = emptyList(),
        collaborationMode: CodexCollaborationModeKind? = null
    ) {
        codexService.sendMessage(
            text,
            attachments = attachments,
            skillMentions = skillMentions,
            collaborationMode = collaborationMode
        )
    }

    suspend fun steerTurn(
        text: String,
        threadId: String,
        attachments: List<CodexImageAttachment> = emptyList(),
        skillMentions: List<CodexTurnSkillMention> = emptyList(),
        collaborationMode: CodexCollaborationModeKind? = null
    ) {
        codexService.steerTurn(
            text = text,
            threadId = threadId,
            attachments = attachments,
            skillMentions = skillMentions,
            collaborationMode = collaborationMode
        )
    }

    suspend fun fuzzyFileSearch(
        query: String,
        roots: List<String>,
        cancellationToken: String? = null
    ): List<CodexFuzzyFileMatch> =
        codexService.fuzzyFileSearch(query, roots, cancellationToken)

    suspend fun listSkills(
        cwds: List<String>?,
        forceReload: Boolean = false
    ): List<CodexSkillMetadata> =
        codexService.listSkills(cwds, forceReload)

    fun stopTurn() {
        codexService.stopTurn()
    }

    fun respondToApproval(approve: Boolean) {
        codexService.respondToApproval(approve)
    }

    fun connect() {
        if (codexService.hasSavedRelay()) {
            codexService.connectFromSavedRelay()
        }
    }

    fun disconnect() {
        codexService.disconnect()
        refreshTrustedPairPresentation()
    }

    fun connectFromQR(payload: CodexPairingQRPayload) {
        codexService.connectFromQR(payload)
        refreshTrustedPairPresentation()
    }

    fun forgetPairing() {
        codexService.forgetPairing()
        refreshTrustedPairPresentation()
    }

    fun hasSavedRelay(): Boolean = codexService.hasSavedRelay()

    fun setAccessMode(mode: CodexAccessMode) {
        codexService.setAccessMode(mode)
    }

    fun setSelectedModel(modelId: String?) {
        codexService.setSelectedModel(modelId)
    }

    fun setSelectedReasoningEffort(effort: String?) {
        codexService.setSelectedReasoningEffort(effort)
    }

    fun setSelectedServiceTier(serviceTier: CodexServiceTier?) {
        codexService.setSelectedServiceTier(serviceTier)
    }

    fun supportedReasoningEffortsForSelectedModel(): List<String> =
        codexService.supportedReasoningEffortsForSelectedModel()

    fun refreshContextWindowUsage(threadId: String) {
        codexService.refreshContextWindowUsage(threadId)
    }

    fun refreshActiveThreadNow(threadId: String? = null) {
        codexService.refreshActiveThreadNow(threadId)
    }

    fun refreshRateLimits() {
        codexService.refreshRateLimits()
    }

    fun respondToStructuredUserInput(
        requestID: JsonValue,
        answersByQuestionID: Map<String, List<String>>
    ) {
        codexService.respondToStructuredUserInput(requestID, answersByQuestionID)
    }

    fun refreshAccountStatus() {
        codexService.refreshAccountStatus()
    }

    fun setTrustedPairNickname(deviceId: String?, nickname: String) {
        codexService.setTrustedPairNickname(deviceId, nickname)
        refreshTrustedPairPresentation()
    }

    fun isThreadRunning(threadId: String): Boolean =
        codexService.isThreadRunning(threadId)

    suspend fun startOrResumeGPTLoginOnPhone(): String? =
        codexService.startOrResumeGPTLoginOnPhone()

    suspend fun cancelGPTLogin() {
        codexService.cancelGPTLogin()
    }

    suspend fun transcribeVoiceClip(wavData: ByteArray, durationMs: Long): String =
        codexService.transcribeVoiceClip(wavData, durationMs)

    suspend fun logoutGPTAccount() {
        codexService.logoutGPTAccount()
    }

    private fun refreshNotificationPromptState() {
        _shouldPromptForNotificationPermission.value =
            codexService.isConnected.value &&
                runCompletionNotifier.shouldAutoPromptForPermission(codexService.isConnected.value)
    }

    suspend fun createPullRequestUrl(cwd: String, currentBranchHint: String? = null): String? =
        codexService.createPullRequestUrl(cwd, currentBranchHint)

    suspend fun gitStatus(threadId: String, cwd: String? = null): GitRepoSyncResult? =
        codexService.gitStatus(threadId, cwd)

    suspend fun gitDiff(threadId: String, cwd: String? = null): GitDiffResult =
        codexService.gitDiff(threadId, cwd)

    suspend fun continueOnMac(threadId: String) {
        codexService.continueOnMac(threadId)
    }

    suspend fun startReview(
        threadId: String,
        target: CodexReviewTarget,
        baseBranch: String? = null
    ) {
        codexService.startReview(threadId, target, baseBranch)
    }

    suspend fun forkThread(
        threadId: String,
        targetProjectPath: String? = null
    ): CodexThread =
        codexService.forkThread(threadId, targetProjectPath)

    suspend fun moveThreadToProjectPath(
        threadId: String,
        projectPath: String
    ): CodexThread =
        codexService.moveThreadToProjectPath(threadId, projectPath)

    suspend fun gitBranchesWithStatus(
        threadId: String,
        cwd: String? = null
    ): GitBranchesWithStatusResult =
        codexService.gitBranchesWithStatus(threadId, cwd)

    suspend fun gitCreateWorktree(
        threadId: String,
        name: String,
        baseBranch: String,
        changeTransfer: GitWorktreeChangeTransferMode = GitWorktreeChangeTransferMode.COPY,
        cwd: String? = null
    ): GitCreateWorktreeResult =
        codexService.gitCreateWorktree(threadId, name, baseBranch, changeTransfer, cwd)

    suspend fun gitRemoveManagedWorktree(cwd: String, branch: String? = null) {
        codexService.gitRemoveManagedWorktree(cwd, branch)
    }

    suspend fun gitCheckout(
        threadId: String,
        branch: String,
        cwd: String? = null
    ): GitCheckoutResult =
        codexService.gitCheckout(threadId, branch, cwd)

    fun assistantRevertPresentation(
        message: CodexMessage,
        workingDirectory: String?
    ): AssistantRevertPresentation? =
        codexService.assistantRevertPresentation(message, workingDirectory)

    fun readyAIChangeSetForMessage(message: CodexMessage): AIChangeSet? =
        codexService.readyAIChangeSetForMessage(message)

    fun diffableAIChangeSetForMessage(message: CodexMessage): AIChangeSet? =
        codexService.diffableAIChangeSetForMessage(message)

    suspend fun previewRevert(changeSetId: String, workingDirectory: String): RevertPreviewResult =
        codexService.previewRevert(changeSetId, workingDirectory)

    suspend fun applyRevert(changeSetId: String, workingDirectory: String): RevertApplyResult =
        codexService.applyRevert(changeSetId, workingDirectory)

    // Git
    fun gitCommit(threadId: String, message: String) {
        viewModelScope.launch { codexService.gitCommit(threadId, message) }
    }

    fun gitPush(threadId: String) {
        viewModelScope.launch { codexService.gitPush(threadId) }
    }

    fun gitPull(threadId: String) {
        viewModelScope.launch { codexService.gitPull(threadId) }
    }

    private fun refreshTrustedPairPresentation() {
        _trustedPairPresentation.value = codexService.trustedPairPresentation()
    }
}
