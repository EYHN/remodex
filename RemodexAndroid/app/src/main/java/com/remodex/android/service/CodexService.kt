package com.remodex.android.service

import android.util.Log
import com.remodex.android.data.model.*
import com.remodex.android.data.store.SecureStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class CodexService(
    private val secureStore: SecureStore,
    private val connectionManager: ConnectionManager,
    private val secureTransport: SecureTransport,
    private val messageTransport: MessageTransport,
    private val historyDecoder: HistoryDecoder,
    private val json: Json
) {
    companion object {
        private const val TAG = "CodexService"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val assistantHandler = AssistantHandler()
    private lateinit var incomingRouter: IncomingRouter

    // --- State ---
    private val _threads = MutableStateFlow<List<CodexThread>>(emptyList())
    val threads: StateFlow<List<CodexThread>> = _threads.asStateFlow()

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    private val _messagesByThread = MutableStateFlow<Map<String, List<CodexMessage>>>(emptyMap())
    val messagesByThread: StateFlow<Map<String, List<CodexMessage>>> = _messagesByThread.asStateFlow()

    private val _runningThreadIDs = MutableStateFlow<Set<String>>(emptySet())
    val runningThreadIDs: StateFlow<Set<String>> = _runningThreadIDs.asStateFlow()

    private val _readyThreadIDs = MutableStateFlow<Set<String>>(emptySet())
    val readyThreadIDs: StateFlow<Set<String>> = _readyThreadIDs.asStateFlow()

    private val _failedThreadIDs = MutableStateFlow<Set<String>>(emptySet())
    val failedThreadIDs: StateFlow<Set<String>> = _failedThreadIDs.asStateFlow()

    private val _connectionPhase = MutableStateFlow(CodexConnectionPhase.OFFLINE)
    val connectionPhase: StateFlow<CodexConnectionPhase> = _connectionPhase.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalRequest?>(null)
    val pendingApproval: StateFlow<ApprovalRequest?> = _pendingApproval.asStateFlow()

    private val _availableModels = MutableStateFlow<List<CodexModelOption>>(emptyList())
    val availableModels: StateFlow<List<CodexModelOption>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<String?>(null)
    val selectedModel: StateFlow<String?> = _selectedModel.asStateFlow()

    private val _selectedAccessMode = MutableStateFlow(CodexAccessMode.ON_REQUEST)
    val selectedAccessMode: StateFlow<CodexAccessMode> = _selectedAccessMode.asStateFlow()

    private val _contextWindowUsage = MutableStateFlow<ContextWindowUsage?>(null)
    val contextWindowUsage: StateFlow<ContextWindowUsage?> = _contextWindowUsage.asStateFlow()

    private val _rateLimitBuckets = MutableStateFlow<List<CodexRateLimitBucket>>(emptyList())
    val rateLimitBuckets: StateFlow<List<CodexRateLimitBucket>> = _rateLimitBuckets.asStateFlow()

    private val _bridgeVersionInfo = MutableStateFlow<String?>(null)
    val bridgeVersionInfo: StateFlow<String?> = _bridgeVersionInfo.asStateFlow()

    val isConnected: StateFlow<Boolean> = connectionManager.isConnected
    val isConnecting: StateFlow<Boolean> = connectionManager.isConnecting
    val secureConnectionState: StateFlow<CodexSecureConnectionState> = secureTransport.state

    private val activeTurnIdByThread = mutableMapOf<String, String>()
    private var syncJob: Job? = null

    // --- Initialize ---

    init {
        incomingRouter = IncomingRouter(
            secureTransport = secureTransport,
            messageTransport = messageTransport,
            json = json,
            onNotification = ::handleNotification,
            onRequest = ::handleServerRequest
        )
        incomingRouter.sendRawCallback = { text -> connectionManager.send(text) }

        // Listen for incoming messages
        scope.launch {
            connectionManager.incomingMessages.collect { text ->
                incomingRouter.processWireMessage(text)
            }
        }

        // React to secure connection state
        scope.launch {
            secureTransport.state.collect { state ->
                when (state) {
                    CodexSecureConnectionState.CONNECTED_ENCRYPTED -> {
                        _connectionPhase.value = CodexConnectionPhase.LOADING_CHATS
                        initializeSession()
                    }
                    CodexSecureConnectionState.DISCONNECTED -> {
                        _connectionPhase.value = CodexConnectionPhase.OFFLINE
                        stopSyncLoops()
                    }
                    CodexSecureConnectionState.HANDSHAKING -> {
                        _connectionPhase.value = CodexConnectionPhase.HANDSHAKING
                    }
                    CodexSecureConnectionState.CONNECTING -> {
                        _connectionPhase.value = CodexConnectionPhase.CONNECTING
                    }
                    CodexSecureConnectionState.ERROR -> {
                        _connectionPhase.value = CodexConnectionPhase.OFFLINE
                    }
                }
            }
        }

        // Load access mode from store
        secureStore.readString(SecureStore.SELECTED_ACCESS_MODE)?.let { mode ->
            _selectedAccessMode.value = try {
                CodexAccessMode.valueOf(mode)
            } catch (_: Exception) { CodexAccessMode.ON_REQUEST }
        }
    }

    // --- Connection ---

    fun connect(relayUrl: String, sessionId: String, handshakeMode: String = "qr_bootstrap") {
        _connectionPhase.value = CodexConnectionPhase.CONNECTING

        scope.launch {
            // Wait for WebSocket to connect, then send clientHello
            connectionManager.connect(relayUrl, sessionId)

            // Observe connection state
            connectionManager.isConnected.first { it }
            _connectionPhase.value = CodexConnectionPhase.HANDSHAKING

            val clientHello = secureTransport.buildClientHello(sessionId, handshakeMode)
            val helloText = json.encodeToString(SecureClientHello.serializer(), clientHello)
            connectionManager.send(helloText)
        }
    }

    fun connectFromSavedRelay() {
        val url = secureStore.readString(SecureStore.RELAY_URL) ?: return
        val sessionId = secureStore.readString(SecureStore.RELAY_SESSION_ID) ?: return
        connect(url, sessionId, "trusted_reconnect")
    }

    fun connectFromQR(payload: CodexPairingQRPayload) {
        // Save relay info
        secureStore.writeString(SecureStore.RELAY_URL, payload.relay)
        secureStore.writeString(SecureStore.RELAY_SESSION_ID, payload.sessionId)
        secureStore.writeString(SecureStore.RELAY_MAC_DEVICE_ID, payload.macDeviceId)
        secureStore.writeString(SecureStore.RELAY_MAC_IDENTITY_PUBLIC_KEY, payload.macIdentityPublicKey)

        connect(payload.relay, payload.sessionId, "qr_bootstrap")
    }

    fun disconnect() {
        stopSyncLoops()
        messageTransport.cancelAllPending("Disconnected")
        connectionManager.disconnect()
        secureTransport.reset()
        assistantHandler.clearStreamingState()
        _connectionPhase.value = CodexConnectionPhase.OFFLINE
    }

    fun hasSavedRelay(): Boolean =
        secureStore.readString(SecureStore.RELAY_URL) != null
                && secureStore.readString(SecureStore.RELAY_SESSION_ID) != null

    fun forgetPairing() {
        disconnect()
        secureStore.deleteValue(SecureStore.RELAY_URL)
        secureStore.deleteValue(SecureStore.RELAY_SESSION_ID)
        secureStore.deleteValue(SecureStore.RELAY_MAC_DEVICE_ID)
        secureStore.deleteValue(SecureStore.RELAY_MAC_IDENTITY_PUBLIC_KEY)
    }

    // --- Session Init ---

    private fun initializeSession() {
        scope.launch {
            try {
                val result = messageTransport.sendRequest("initialize", JsonValue.obj(
                    "capabilities" to JsonValue.obj()
                ))
                Log.d(TAG, "Session initialized")

                // Fetch account status
                fetchAccountStatus()

                _connectionPhase.value = CodexConnectionPhase.SYNCING
                // Start sync loops
                startSyncLoops()

                _connectionPhase.value = CodexConnectionPhase.CONNECTED
            } catch (e: Exception) {
                Log.e(TAG, "Initialize failed: ${e.message}")
            }
        }
    }

    private fun fetchAccountStatus() {
        scope.launch {
            try {
                val result = messageTransport.sendRequest("account/status/read", JsonValue.obj(
                    "refreshToken" to JsonValue.bool(false),
                    "includeToken" to JsonValue.bool(false)
                ))
                val data = result.result?.objectValue
                data?.get("bridgeVersionInfo")?.objectValue?.get("version")?.stringValue?.let {
                    _bridgeVersionInfo.value = it
                }
            } catch (_: Exception) {}
        }
    }

    // --- Thread Operations ---

    fun selectThread(threadId: String?) {
        _activeThreadId.value = threadId
        if (threadId != null) {
            loadThreadHistory(threadId)
        }
    }

    fun startNewThread(projectPath: String? = null) {
        scope.launch {
            try {
                val params = mutableMapOf<String, JsonValue>()
                projectPath?.let { params["cwd"] = JsonValue.string(it) }
                val result = messageTransport.sendRequest("thread/start",
                    JsonValue.ObjectValue(params))
                val threadId = result.result?.objectValue?.get("threadId")?.stringValue
                    ?: result.result?.objectValue?.get("id")?.stringValue
                if (threadId != null) {
                    syncThreadList()
                    selectThread(threadId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start thread failed: ${e.message}")
            }
        }
    }

    fun sendMessage(
        text: String,
        threadId: String? = null,
        attachments: List<CodexImageAttachment> = emptyList()
    ) {
        val tid = threadId ?: _activeThreadId.value ?: return
        if (text.isBlank() && attachments.isEmpty()) return

        // Optimistic UI: add pending message
        val optimisticMsg = CodexMessage(
            threadId = tid,
            role = CodexMessageRole.USER,
            kind = CodexMessageKind.CHAT,
            text = text,
            orderIndex = CodexMessageOrderCounter.next(),
            deliveryState = CodexMessageDeliveryState.PENDING,
            attachments = attachments
        )
        appendMessage(tid, optimisticMsg)

        scope.launch {
            try {
                val params = mutableMapOf<String, JsonValue>(
                    "threadId" to JsonValue.string(tid),
                    "content" to JsonValue.string(text)
                )

                if (attachments.isNotEmpty()) {
                    params["images"] = JsonValue.array(*attachments.map { att ->
                        JsonValue.obj(
                            "url" to JsonValue.string(att.payloadDataURL ?: ""),
                            "thumbnail" to JsonValue.string(att.thumbnailBase64JPEG ?: "")
                        )
                    }.toTypedArray())
                }

                _selectedModel.value?.let { params["model"] = JsonValue.string(it) }

                val result = messageTransport.sendRequest("turn/start",
                    JsonValue.ObjectValue(params))

                // Mark as confirmed
                updateMessageDeliveryState(tid, optimisticMsg.id, CodexMessageDeliveryState.CONFIRMED)

                // Mark thread as running
                markThreadRunning(tid)
            } catch (e: Exception) {
                Log.e(TAG, "Send message failed: ${e.message}")
                updateMessageDeliveryState(tid, optimisticMsg.id, CodexMessageDeliveryState.FAILED)
            }
        }
    }

    fun stopTurn(threadId: String? = null) {
        val tid = threadId ?: _activeThreadId.value ?: return
        val turnId = activeTurnIdByThread[tid]

        scope.launch {
            try {
                val params = mutableMapOf<String, JsonValue>(
                    "threadId" to JsonValue.string(tid)
                )
                turnId?.let { params["turnId"] = JsonValue.string(it) }
                messageTransport.sendRequest("turn/stop", JsonValue.ObjectValue(params))
            } catch (e: Exception) {
                Log.e(TAG, "Stop turn failed: ${e.message}")
            }
        }
    }

    // --- Approval ---

    fun respondToApproval(approve: Boolean) {
        val approval = _pendingApproval.value ?: return
        _pendingApproval.value = null

        val decision = if (approve) "accept" else "reject"
        messageTransport.sendResponse(
            approval.requestId,
            JsonValue.obj("decision" to JsonValue.string(decision))
        )
    }

    fun setAccessMode(mode: CodexAccessMode) {
        _selectedAccessMode.value = mode
        secureStore.writeString(SecureStore.SELECTED_ACCESS_MODE, mode.name)
    }

    fun setSelectedModel(modelId: String?) {
        _selectedModel.value = modelId
    }

    // --- Notifications ---

    private fun handleNotification(method: String, params: JsonValue?) {
        val p = params?.objectValue ?: emptyMap()
        val threadId = p["threadId"]?.stringValue
            ?: p["thread_id"]?.stringValue
        val turnId = p["turnId"]?.stringValue
            ?: p["turn_id"]?.stringValue
        val itemId = p["itemId"]?.stringValue
            ?: p["item_id"]?.stringValue

        when {
            method.contains("turn/started") || method.contains("turn_started") -> {
                if (threadId != null) {
                    markThreadRunning(threadId)
                    if (turnId != null) activeTurnIdByThread[threadId] = turnId
                }
            }
            method.contains("turn/completed") || method.contains("turn_completed") -> {
                if (threadId != null) {
                    clearRunningState(threadId)
                    _readyThreadIDs.value = _readyThreadIDs.value + threadId
                    activeTurnIdByThread.remove(threadId)
                    assistantHandler.clearStreamingState()
                }
            }
            method.contains("turn/failed") || method.contains("turn_failed") -> {
                if (threadId != null) {
                    clearRunningState(threadId)
                    _failedThreadIDs.value = _failedThreadIDs.value + threadId
                    activeTurnIdByThread.remove(threadId)
                }
            }
            method.contains("item/agent/delta") || method.contains("item_agent_delta") ||
            method.contains("agent.delta") -> {
                val text = p["delta"]?.stringValue
                    ?: p["text"]?.stringValue ?: ""
                if (threadId != null && text.isNotEmpty()) {
                    val messages = getThreadMessages(threadId).toMutableList()
                    assistantHandler.appendAgentDelta(threadId, turnId, itemId, text, messages)
                    setThreadMessages(threadId, messages)
                }
            }
            method.contains("item/agent/completed") || method.contains("item_agent_completed") ||
            method.contains("agent.completed") -> {
                val text = p["text"]?.stringValue ?: p["content"]?.stringValue
                if (threadId != null) {
                    val messages = getThreadMessages(threadId).toMutableList()
                    assistantHandler.completeAgentMessage(threadId, turnId, itemId, text, messages)
                    setThreadMessages(threadId, messages)
                }
            }
            method.contains("item/agent/started") || method.contains("item_agent_started") -> {
                // Agent item started — no action needed beyond running state
            }
            method.contains("item/fileChange") || method.contains("file_change") -> {
                val text = p["path"]?.stringValue ?: p["summary"]?.stringValue ?: ""
                if (threadId != null && text.isNotEmpty()) {
                    val messages = getThreadMessages(threadId).toMutableList()
                    assistantHandler.appendSystemMessage(
                        threadId, turnId, itemId,
                        CodexMessageKind.FILE_CHANGE, text, messages
                    )
                    setThreadMessages(threadId, messages)
                }
            }
            method.contains("item/commandExecution") || method.contains("command_execution") -> {
                val command = p["command"]?.stringValue ?: p["call"]?.stringValue ?: ""
                if (threadId != null) {
                    val messages = getThreadMessages(threadId).toMutableList()
                    val details = CommandExecutionDetails(
                        fullCommand = command,
                        cwd = p["cwd"]?.stringValue,
                        exitCode = p["exitCode"]?.intValue?.toInt(),
                        outputTail = p["output"]?.stringValue ?: ""
                    )
                    assistantHandler.appendSystemMessage(
                        threadId, turnId, itemId,
                        CodexMessageKind.COMMAND_EXECUTION, "$ $command", messages,
                        commandDetails = details
                    )
                    setThreadMessages(threadId, messages)
                }
            }
            method.contains("item/reasoning") || method.contains("reasoning") -> {
                val text = p["text"]?.stringValue ?: p["reasoning"]?.stringValue ?: ""
                if (threadId != null && text.isNotEmpty()) {
                    val messages = getThreadMessages(threadId).toMutableList()
                    assistantHandler.appendSystemMessage(
                        threadId, turnId, itemId,
                        CodexMessageKind.THINKING, text, messages
                    )
                    setThreadMessages(threadId, messages)
                }
            }
            method.contains("thread/list/update") || method.contains("thread_list_update") -> {
                scope.launch { syncThreadList() }
            }
            method.contains("models/available") -> {
                val models = p["models"]?.arrayValue?.mapNotNull { m ->
                    try { json.decodeFromString(CodexModelOption.serializer(),
                        json.encodeToString(JsonValue.serializer(), m)) }
                    catch (_: Exception) { null }
                } ?: emptyList()
                _availableModels.value = models
            }
        }
    }

    private fun handleServerRequest(id: JsonValue, method: String, params: JsonValue?) {
        val p = params?.objectValue ?: emptyMap()

        when {
            method.contains("approval") || method.contains("confirm") -> {
                // Check auto-approve
                if (_selectedAccessMode.value == CodexAccessMode.FULL_ACCESS) {
                    messageTransport.sendResponse(id,
                        JsonValue.obj("decision" to JsonValue.string("accept")))
                    return
                }

                _pendingApproval.value = ApprovalRequest(
                    requestId = id,
                    toolName = p["tool"]?.stringValue ?: p["command"]?.stringValue ?: "Unknown",
                    description = p["description"]?.stringValue
                        ?: p["message"]?.stringValue ?: "",
                    threadId = p["threadId"]?.stringValue
                )
            }
            method.contains("structuredInput") || method.contains("structured_input") -> {
                // For now, auto-dismiss structured input requests
                Log.d(TAG, "Structured input request: $method")
            }
            else -> {
                Log.d(TAG, "Unhandled server request: $method")
            }
        }
    }

    // --- Sync ---

    private fun startSyncLoops() {
        syncJob?.cancel()
        syncJob = scope.launch {
            launch { threadListSyncLoop() }
            launch { activeThreadSyncLoop() }
        }
    }

    private fun stopSyncLoops() {
        syncJob?.cancel()
        syncJob = null
    }

    private suspend fun threadListSyncLoop() {
        while (true) {
            syncThreadList()
            delay(10_000) // 10s interval
        }
    }

    private suspend fun activeThreadSyncLoop() {
        while (true) {
            val tid = _activeThreadId.value
            if (tid != null) {
                loadThreadHistory(tid)
            }
            val interval = if (_runningThreadIDs.value.any { it == _activeThreadId.value }) {
                3_000L // 3s when active turn running
            } else {
                10_000L // 10s idle
            }
            delay(interval)
        }
    }

    suspend fun syncThreadList() {
        try {
            val result = messageTransport.sendRequest("thread/list", JsonValue.obj(
                "limit" to JsonValue.int(50)
            ))
            val threadList = result.result?.objectValue?.get("threads")?.arrayValue
                ?: result.result?.arrayValue
            if (threadList != null) {
                val decoded = threadList.mapNotNull { t ->
                    val elem = json.parseToJsonElement(json.encodeToString(JsonValue.serializer(), t))
                    CodexThread.fromJson(json, elem)
                }
                _threads.value = decoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync thread list failed: ${e.message}")
        }
    }

    private fun loadThreadHistory(threadId: String) {
        scope.launch {
            try {
                val result = messageTransport.sendRequest("thread/read", JsonValue.obj(
                    "threadId" to JsonValue.string(threadId),
                    "includeTurns" to JsonValue.bool(true)
                ))
                val messages = historyDecoder.decodeMessagesFromThreadRead(threadId, result.result)
                if (messages.isNotEmpty()) {
                    setThreadMessages(threadId, messages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load history failed: ${e.message}")
            }
        }
    }

    // --- Context Window & Rate Limits ---

    fun refreshContextWindowUsage(threadId: String) {
        scope.launch {
            try {
                val result = messageTransport.sendRequest("thread/contextWindow/read", JsonValue.obj(
                    "threadId" to JsonValue.string(threadId)
                ))
                val usage = result.result?.objectValue?.get("usage")?.objectValue
                if (usage != null) {
                    _contextWindowUsage.value = ContextWindowUsage(
                        tokensUsed = usage["tokensUsed"]?.intValue ?: 0,
                        tokenLimit = usage["tokenLimit"]?.intValue ?: 0
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshRateLimits() {
        scope.launch {
            try {
                val result = messageTransport.sendRequest("account/rateLimits/read")
                val buckets = result.result?.objectValue?.get("buckets")?.arrayValue
                if (buckets != null) {
                    _rateLimitBuckets.value = buckets.mapNotNull { b ->
                        try { json.decodeFromString(CodexRateLimitBucket.serializer(),
                            json.encodeToString(JsonValue.serializer(), b)) }
                        catch (_: Exception) { null }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // --- Git Actions ---

    suspend fun gitStatus(threadId: String): GitRepoSyncResult? {
        return try {
            val result = messageTransport.sendRequest("git/status", JsonValue.obj(
                "threadId" to JsonValue.string(threadId)
            ))
            json.decodeFromString(GitRepoSyncResult.serializer(),
                json.encodeToString(JsonValue.serializer(), result.result ?: return null))
        } catch (_: Exception) { null }
    }

    suspend fun gitCommit(threadId: String, message: String): GitCommitResult? {
        return try {
            val result = messageTransport.sendRequest("git/commit", JsonValue.obj(
                "threadId" to JsonValue.string(threadId),
                "message" to JsonValue.string(message)
            ))
            json.decodeFromString(GitCommitResult.serializer(),
                json.encodeToString(JsonValue.serializer(), result.result ?: return null))
        } catch (_: Exception) { null }
    }

    suspend fun gitPush(threadId: String): GitPushResult? {
        return try {
            val result = messageTransport.sendRequest("git/push", JsonValue.obj(
                "threadId" to JsonValue.string(threadId)
            ))
            json.decodeFromString(GitPushResult.serializer(),
                json.encodeToString(JsonValue.serializer(), result.result ?: return null))
        } catch (_: Exception) { null }
    }

    suspend fun gitPull(threadId: String): GitPullResult? {
        return try {
            val result = messageTransport.sendRequest("git/pull", JsonValue.obj(
                "threadId" to JsonValue.string(threadId)
            ))
            json.decodeFromString(GitPullResult.serializer(),
                json.encodeToString(JsonValue.serializer(), result.result ?: return null))
        } catch (_: Exception) { null }
    }

    // --- Helpers ---

    private fun getThreadMessages(threadId: String): List<CodexMessage> =
        _messagesByThread.value[threadId] ?: emptyList()

    private fun setThreadMessages(threadId: String, messages: List<CodexMessage>) {
        _messagesByThread.value = _messagesByThread.value.toMutableMap().apply {
            this[threadId] = messages.sortedBy { it.orderIndex }
        }
    }

    private fun appendMessage(threadId: String, message: CodexMessage) {
        val current = getThreadMessages(threadId).toMutableList()
        current.add(message)
        setThreadMessages(threadId, current)
    }

    private fun updateMessageDeliveryState(threadId: String, messageId: String, state: CodexMessageDeliveryState) {
        val current = getThreadMessages(threadId).toMutableList()
        val idx = current.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(deliveryState = state)
            setThreadMessages(threadId, current)
        }
    }

    private fun markThreadRunning(threadId: String) {
        _runningThreadIDs.value = _runningThreadIDs.value + threadId
        _readyThreadIDs.value = _readyThreadIDs.value - threadId
        _failedThreadIDs.value = _failedThreadIDs.value - threadId
    }

    private fun clearRunningState(threadId: String) {
        _runningThreadIDs.value = _runningThreadIDs.value - threadId
    }

    fun isThreadRunning(threadId: String): Boolean =
        threadId in _runningThreadIDs.value
}

data class ApprovalRequest(
    val requestId: JsonValue,
    val toolName: String,
    val description: String,
    val threadId: String? = null
)
