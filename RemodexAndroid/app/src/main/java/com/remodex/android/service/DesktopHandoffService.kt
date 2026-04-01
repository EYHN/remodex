package com.remodex.android.service

import android.util.Log
import com.remodex.android.data.model.JsonValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DesktopHandoffService @Inject constructor(
    private val messageTransport: MessageTransport,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "DesktopHandoffService"
        private const val METHOD = "desktop/continueOnMac"
    }

    sealed class HandoffResult {
        object Success : HandoffResult()
        data class Failed(val message: String) : HandoffResult()
        object Unsupported : HandoffResult()
    }

    suspend fun continueOnMac(threadId: String): HandoffResult {
        val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() }
            ?: return HandoffResult.Failed("This chat does not have a valid thread id yet.")

        if (!connectionManager.isConnected.value) {
            return HandoffResult.Failed("Not connected to your Mac.")
        }

        return try {
            val response = messageTransport.sendRequest(
                METHOD,
                JsonValue.obj("threadId" to JsonValue.string(normalizedThreadId))
            )

            val rpcError = response.error
            if (rpcError != null) {
                val errorCode = rpcError.errorCode
                Log.w(TAG, "$METHOD error ($errorCode): ${rpcError.message}")
                return when (errorCode) {
                    "unsupported_platform" -> HandoffResult.Unsupported
                    else -> HandoffResult.Failed(userMessage(errorCode, rpcError.message))
                }
            }

            val success = response.result
                ?.objectValue
                ?.get("success")
                ?.boolValue == true

            if (success) {
                HandoffResult.Success
            } else {
                HandoffResult.Failed("The Mac app did not return a valid response.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "$METHOD transport error: ${e.message}", e)
            HandoffResult.Failed(e.message ?: "Could not continue this chat on your Mac.")
        }
    }

    private fun userMessage(errorCode: String?, fallback: String?): String {
        return when (errorCode) {
            "missing_thread_id" -> "This chat does not have a valid thread id yet."
            "unsupported_platform" -> "Mac handoff works only when the bridge is running on macOS."
            "handoff_failed" -> fallback ?: "Could not relaunch Codex.app on your Mac."
            else -> fallback ?: "Could not continue this chat on your Mac."
        }
    }
}
