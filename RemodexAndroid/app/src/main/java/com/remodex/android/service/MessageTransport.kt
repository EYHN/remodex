package com.remodex.android.service

import android.util.Log
import com.remodex.android.data.model.JsonValue
import com.remodex.android.data.model.RpcMessage
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageTransport(
    private val connectionManager: ConnectionManager,
    private val secureTransport: SecureTransport,
    private val json: Json
) {
    companion object {
        private const val TAG = "MessageTransport"
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<RpcMessage>>()

    fun handleIncomingRpc(text: String): RpcMessage? {
        return try {
            val msg = json.decodeFromString(RpcMessage.serializer(), text)
            // If it's a response, resolve the pending request
            if (msg.isResponse) {
                val key = msg.requestIdKey
                if (key != null) {
                    pendingRequests.remove(key)?.complete(msg)
                    return null // Response was consumed
                }
            }
            msg // Return request/notification for routing
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RPC: ${e.message}")
            null
        }
    }

    suspend fun sendRequest(method: String, params: JsonValue? = null): RpcMessage {
        val requestId = UUID.randomUUID().toString()
        val msg = RpcMessage.request(requestId, method, params)
        val deferred = CompletableDeferred<RpcMessage>()
        pendingRequests[requestId] = deferred

        sendRaw(msg)

        return withTimeout(REQUEST_TIMEOUT_MS) {
            try {
                deferred.await()
            } finally {
                pendingRequests.remove(requestId)
            }
        }
    }

    fun sendNotification(method: String, params: JsonValue? = null) {
        val msg = RpcMessage.notification(method, params)
        sendRaw(msg)
    }

    fun sendResponse(id: JsonValue, result: JsonValue) {
        val msg = RpcMessage.response(id, result)
        sendRaw(msg)
    }

    fun sendErrorResponse(id: JsonValue?, error: com.remodex.android.data.model.RpcError) {
        val msg = RpcMessage.errorResponse(id, error)
        sendRaw(msg)
    }

    private fun sendRaw(msg: RpcMessage) {
        val text = json.encodeToString(RpcMessage.serializer(), msg)

        if (secureTransport.isEncrypted) {
            val envelope = secureTransport.encryptMessage(text) ?: run {
                Log.e(TAG, "Failed to encrypt message")
                return
            }
            val envelopeText = json.encodeToString(SecureEnvelope.serializer(), envelope)
            connectionManager.send(envelopeText)
        } else {
            connectionManager.send(text)
        }
    }

    fun cancelAllPending(reason: String) {
        val exception = CancellationException(reason)
        pendingRequests.values.forEach { it.completeExceptionally(exception) }
        pendingRequests.clear()
    }
}
