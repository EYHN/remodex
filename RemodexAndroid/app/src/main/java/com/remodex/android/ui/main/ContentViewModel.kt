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
import javax.inject.Inject

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val codexService: CodexService,
    private val secureStore: SecureStore,
    private val secureTransport: SecureTransport,
    private val json: Json
) : ViewModel() {

    // Expose service state
    val threads = codexService.threads
    val activeThreadId = codexService.activeThreadId
    val messagesByThread = codexService.messagesByThread
    val isConnected = codexService.isConnected
    val isConnecting = codexService.isConnecting
    val connectionPhase = codexService.connectionPhase
    val secureConnectionState = codexService.secureConnectionState
    val runningThreadIDs = codexService.runningThreadIDs
    val readyThreadIDs = codexService.readyThreadIDs
    val failedThreadIDs = codexService.failedThreadIDs
    val pendingApproval = codexService.pendingApproval
    val availableModels = codexService.availableModels
    val selectedModel = codexService.selectedModel
    val selectedAccessMode = codexService.selectedAccessMode
    val contextWindowUsage = codexService.contextWindowUsage
    val rateLimitBuckets = codexService.rateLimitBuckets
    val bridgeVersionInfo = codexService.bridgeVersionInfo

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

    init {
        // Auto-reconnect if saved relay exists
        if (codexService.hasSavedRelay() && _hasSeenOnboarding.value) {
            codexService.connectFromSavedRelay()
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

    fun sendMessage(text: String, attachments: List<CodexImageAttachment> = emptyList()) {
        codexService.sendMessage(text, attachments = attachments)
    }

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
    }

    fun connectFromQR(payload: CodexPairingQRPayload) {
        codexService.connectFromQR(payload)
    }

    fun forgetPairing() {
        codexService.forgetPairing()
    }

    fun hasSavedRelay(): Boolean = codexService.hasSavedRelay()

    fun setAccessMode(mode: CodexAccessMode) {
        codexService.setAccessMode(mode)
    }

    fun setSelectedModel(modelId: String?) {
        codexService.setSelectedModel(modelId)
    }

    fun refreshRateLimits() {
        codexService.refreshRateLimits()
    }

    fun isThreadRunning(threadId: String): Boolean =
        codexService.isThreadRunning(threadId)

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
}
