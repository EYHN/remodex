package com.remodex.android.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ConnectionPresentationCoordinator(
    scope: CoroutineScope,
    isConnecting: StateFlow<Boolean>,
    isConnected: StateFlow<Boolean>,
    secureState: StateFlow<CodexSecureConnectionState>,
    threadCount: Flow<Int>
) {
    private data class LoadingState(
        val isBootstrappingConnectionSync: Boolean,
        val isLoadingThreads: Boolean,
        val isLoadingModels: Boolean
    )

    private val _connectionRecoveryState =
        MutableStateFlow<CodexConnectionRecoveryState>(CodexConnectionRecoveryState.Idle)
    val connectionRecoveryState: StateFlow<CodexConnectionRecoveryState> =
        _connectionRecoveryState.asStateFlow()

    private val _isBootstrappingConnectionSync = MutableStateFlow(false)
    val isBootstrappingConnectionSync: StateFlow<Boolean> =
        _isBootstrappingConnectionSync.asStateFlow()

    private val _isLoadingThreads = MutableStateFlow(false)
    val isLoadingThreads: StateFlow<Boolean> = _isLoadingThreads.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val loadingState: Flow<LoadingState> = combine(
        _isBootstrappingConnectionSync,
        _isLoadingThreads,
        _isLoadingModels
    ) { isBootstrapping, isLoadingThreads, isLoadingModels ->
        LoadingState(
            isBootstrappingConnectionSync = isBootstrapping,
            isLoadingThreads = isLoadingThreads,
            isLoadingModels = isLoadingModels
        )
    }

    val connectionPhase: StateFlow<CodexConnectionPhase> = combine(
        isConnecting,
        isConnected,
        secureState,
        threadCount,
        loadingState
    ) { currentlyConnecting, currentlyConnected, secureConnectionState, currentThreadCount, currentLoadingState ->
        deriveConnectionPhase(
            isConnecting = currentlyConnecting,
            isConnected = currentlyConnected,
            secureState = secureConnectionState,
            threadCount = currentThreadCount,
            isBootstrappingConnectionSync = currentLoadingState.isBootstrappingConnectionSync,
            isLoadingThreads = currentLoadingState.isLoadingThreads,
            isLoadingModels = currentLoadingState.isLoadingModels
        )
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        CodexConnectionPhase.OFFLINE
    )

    fun prepareForConnection(isTrustedReconnect: Boolean) {
        clearTransientLoading()
        _connectionRecoveryState.value = if (isTrustedReconnect) {
            CodexConnectionRecoveryState.Retrying(
                attempt = 0,
                message = "Preparing reconnect..."
            )
        } else {
            CodexConnectionRecoveryState.Idle
        }
    }

    fun clearTransientLoading() {
        _isBootstrappingConnectionSync.value = false
        _isLoadingThreads.value = false
        _isLoadingModels.value = false
    }

    fun resetForManualDisconnect() {
        clearTransientLoading()
        markRecoveryIdle()
    }

    fun setBootstrapping(active: Boolean) {
        _isBootstrappingConnectionSync.value = active
    }

    fun setLoadingThreads(active: Boolean) {
        _isLoadingThreads.value = active
    }

    fun setLoadingModels(active: Boolean) {
        _isLoadingModels.value = active
    }

    fun markRecoveryIdle() {
        _connectionRecoveryState.value = CodexConnectionRecoveryState.Idle
    }

    fun applyReconnectStatus(reconnectStatus: RelayReconnectStatus?) {
        _connectionRecoveryState.value = if (reconnectStatus != null) {
            CodexConnectionRecoveryState.Retrying(
                attempt = reconnectStatus.attempt,
                message = "Reconnecting in ${formatReconnectDelaySeconds(reconnectStatus.delayMs)}..."
            )
        } else if (_connectionRecoveryState.value is CodexConnectionRecoveryState.Retrying) {
            CodexConnectionRecoveryState.Idle
        } else {
            _connectionRecoveryState.value
        }
    }

    private fun deriveConnectionPhase(
        isConnecting: Boolean,
        isConnected: Boolean,
        secureState: CodexSecureConnectionState,
        threadCount: Int,
        isBootstrappingConnectionSync: Boolean,
        isLoadingThreads: Boolean,
        isLoadingModels: Boolean
    ): CodexConnectionPhase {
        return when {
            secureState == CodexSecureConnectionState.HANDSHAKING -> CodexConnectionPhase.HANDSHAKING
            isConnecting || secureState == CodexSecureConnectionState.CONNECTING -> CodexConnectionPhase.CONNECTING
            !isConnected || secureState != CodexSecureConnectionState.CONNECTED_ENCRYPTED ->
                CodexConnectionPhase.OFFLINE
            threadCount == 0 && (isBootstrappingConnectionSync || isLoadingThreads) ->
                CodexConnectionPhase.LOADING_CHATS
            isBootstrappingConnectionSync || isLoadingThreads || isLoadingModels ->
                CodexConnectionPhase.SYNCING
            else -> CodexConnectionPhase.CONNECTED
        }
    }

    private fun formatReconnectDelaySeconds(delayMs: Long): String {
        if (delayMs <= 0L) {
            return "0s"
        }

        val seconds = delayMs / 1000.0
        return if (seconds >= 10 || delayMs % 1000L == 0L) {
            "${seconds.toInt()}s"
        } else {
            String.format("%.1fs", seconds)
        }
    }
}
