package com.remodex.android.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*

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

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private var currentRelayUrl: String? = null
    private var currentSessionId: String? = null

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
                Log.d(TAG, "WebSocket connected")
                _isConnected.value = true
                _isConnecting.value = false
                reconnectAttempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    _incomingMessages.emit(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                handleDisconnect(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
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
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _isConnected.value = false
        _isConnecting.value = false
    }

    private fun handleDisconnect(code: Int) {
        _isConnected.value = false
        _isConnecting.value = false

        // Don't reconnect on intentional close codes
        when (code) {
            4001 -> { Log.w(TAG, "Mac replaced, not reconnecting"); return }
            4003 -> { Log.w(TAG, "iPhone replaced, not reconnecting"); return }
            4000, 4002 -> { Log.w(TAG, "Session unavailable ($code)"); return }
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
            reconnectAttempt++
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
    }
}
