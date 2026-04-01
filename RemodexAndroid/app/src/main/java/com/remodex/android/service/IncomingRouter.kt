package com.remodex.android.service

import android.util.Log
import com.remodex.android.data.model.JsonValue
import com.remodex.android.data.model.RpcMessage
import kotlinx.serialization.json.*

class IncomingRouter(
    private val secureTransport: SecureTransport,
    private val messageTransport: MessageTransport,
    private val json: Json,
    private val onNotification: (method: String, params: JsonValue?) -> Unit,
    private val onRequest: (id: JsonValue, method: String, params: JsonValue?) -> Unit
) {
    companion object {
        private const val TAG = "IncomingRouter"
        private val SECURE_KINDS = setOf(
            "clientHello", "serverHello", "clientAuth",
            "secureReady", "secureError", "encryptedEnvelope",
            "resumeState", "relayMacRegistration"
        )
    }

    fun processWireMessage(text: String) {
        try {
            val element = json.parseToJsonElement(text)
            val obj = element.jsonObject
            val kind = obj["kind"]?.jsonPrimitive?.contentOrNull

            if (kind != null && kind in SECURE_KINDS) {
                handleSecureMessage(kind, text)
                return
            }

            // It's an application-layer RPC message
            handleRpcMessage(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process wire message: ${e.message}")
        }
    }

    private fun handleSecureMessage(kind: String, text: String) {
        when (kind) {
            "serverHello" -> {
                val hello = json.decodeFromString(SecureServerHello.serializer(), text)
                val auth = secureTransport.processServerHello(hello)
                if (auth != null) {
                    val authText = json.encodeToString(SecureClientAuth.serializer(), auth)
                    // Need to send raw (not encrypted)
                    sendRawCallback?.invoke(authText)
                }
            }
            "secureReady" -> {
                val ready = json.decodeFromString(SecureReadyMessage.serializer(), text)
                val resumeState = secureTransport.processSecureReady(ready)
                if (resumeState != null) {
                    val resumeText = json.encodeToString(SecureResumeState.serializer(), resumeState)
                    sendRawCallback?.invoke(resumeText)
                }
            }
            "secureError" -> {
                val error = json.decodeFromString(SecureErrorMessage.serializer(), text)
                secureTransport.processSecureError(error)
            }
            "encryptedEnvelope" -> {
                val envelope = json.decodeFromString(SecureEnvelope.serializer(), text)
                val plaintext = secureTransport.decryptEnvelope(envelope)
                if (plaintext != null) {
                    handleRpcMessage(plaintext)
                }
            }
        }
    }

    private fun handleRpcMessage(text: String) {
        val msg = messageTransport.handleIncomingRpc(text) ?: return // Was a response, consumed

        when {
            msg.isRequest -> {
                val id = msg.id ?: return
                val method = msg.method ?: return
                Log.d(TAG, "Server request: $method")
                onRequest(id, method, msg.params)
            }
            msg.isNotification -> {
                val method = msg.method ?: return
                onNotification(normalizeMethod(method), msg.params)
            }
        }
    }

    private fun normalizeMethod(method: String): String {
        // Strip "codex/event/" prefix if present
        val stripped = method.removePrefix("codex/event/").removePrefix("codex/")
        return stripped
    }

    var sendRawCallback: ((String) -> Unit)? = null
}
