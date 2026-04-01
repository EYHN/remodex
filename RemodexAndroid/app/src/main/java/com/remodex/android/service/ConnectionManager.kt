package com.remodex.android.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap

data class RelayReconnectStatus(
    val attempt: Int,
    val delayMs: Long
)

class ConnectionManager(
    private val okHttpClient: OkHttpClient,
    private val secureTransport: SecureTransport,
    private val json: Json
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val BASE_RECONNECT_DELAY_MS = 1_000L
        const val MAX_MESSAGE_SIZE_BYTES = 16 * 1024 * 1024 // 16 MB
    }

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _reconnectStatus = MutableStateFlow<RelayReconnectStatus?>(null)
    val reconnectStatus: StateFlow<RelayReconnectStatus?> = _reconnectStatus.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private var currentRelayUrl: String? = null
    private var currentSessionId: String? = null
    private val intentionallyClosingSockets = ConcurrentHashMap.newKeySet<WebSocket>()

    private fun isCurrentSocket(socket: WebSocket): Boolean = webSocket === socket

    fun connect(relayUrl: String, sessionId: String) {
        disconnect()
        currentRelayUrl = relayUrl
        currentSessionId = sessionId
        _isConnecting.value = true

        val wsUrl = "${relayUrl.trimEnd('/')}/$sessionId"
        Log.d(TAG, "Connecting to relay: ${wsUrl.take(40)}...")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("x-role", "iphone")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(webSocket)) {
                    Log.d(TAG, "Ignoring open from stale socket")
                    intentionallyClosingSockets.add(webSocket)
                    webSocket.close(1000, "Superseded socket")
                    return
                }
                Log.d(TAG, "WebSocket connected")
                _isConnected.value = true
                _isConnecting.value = false
                _reconnectStatus.value = null
                reconnectAttempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentSocket(webSocket)) {
                    Log.d(TAG, "Ignoring message from stale socket")
                    return
                }
                scope.launch {
                    _incomingMessages.emit(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) {
                    Log.d(TAG, "Ignoring closing event from stale socket: $code $reason")
                    intentionallyClosingSockets.remove(webSocket)
                    webSocket.close(1000, null)
                    return
                }
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                if (intentionallyClosingSockets.remove(webSocket)) {
                    Log.d(TAG, "Ignoring intentional close")
                    return
                }
                if (!isCurrentSocket(webSocket)) {
                    Log.d(TAG, "Ignoring close from stale socket")
                    return
                }
                handleDisconnect(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                if (intentionallyClosingSockets.remove(webSocket)) {
                    Log.d(TAG, "Ignoring failure from intentional close")
                    return
                }
                if (!isCurrentSocket(webSocket)) {
                    Log.d(TAG, "Ignoring failure from stale socket")
                    return
                }
                handleDisconnect(-1)
            }
        })
    }

    fun send(text: String): Boolean {
        if (text.toByteArray().size > MAX_MESSAGE_SIZE_BYTES) {
            Log.e(TAG, "Message exceeds max size, dropping")
            return false
        }
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.let { socket ->
            intentionallyClosingSockets.add(socket)
            socket.close(1000, "Client disconnect")
        }
        webSocket = null
        _isConnected.value = false
        _isConnecting.value = false
        _reconnectStatus.value = null
    }

    private fun handleDisconnect(code: Int) {
        _isConnected.value = false
        _isConnecting.value = false

        // Don't reconnect on intentional close codes
        when (code) {
            4001 -> { Log.w(TAG, "Mac replaced, not reconnecting"); _reconnectStatus.value = null; return }
            4003 -> { Log.w(TAG, "iPhone replaced, not reconnecting"); _reconnectStatus.value = null; return }
            4000, 4002 -> { Log.w(TAG, "Session unavailable ($code)"); _reconnectStatus.value = null; return }
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val url = currentRelayUrl ?: return
        val session = currentSessionId ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = minOf(
                BASE_RECONNECT_DELAY_MS * (1L shl minOf(reconnectAttempt, 5)),
                MAX_RECONNECT_DELAY_MS
            )
            val nextAttempt = reconnectAttempt + 1
            _reconnectStatus.value = RelayReconnectStatus(
                attempt = nextAttempt,
                delayMs = delayMs
            )
            reconnectAttempt = nextAttempt
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
            delay(delayMs)
            connect(url, session)
        }
    }

    fun updateSession(relayUrl: String, sessionId: String) {
        currentRelayUrl = relayUrl
        currentSessionId = sessionId
    }

    fun resetReconnectState() {
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        _reconnectStatus.value = null
    }
}
