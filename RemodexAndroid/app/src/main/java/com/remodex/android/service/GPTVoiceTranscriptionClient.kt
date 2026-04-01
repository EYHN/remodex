package com.remodex.android.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CodexVoiceTranscriptionPreflight(
    val byteCount: Int,
    val durationMs: Long
) {
    companion object {
        const val MAX_DURATION_MS = 60_000L
        const val MAX_BYTE_COUNT = 10 * 1024 * 1024
    }

    fun validate() {
        if (durationMs <= 0L) {
            throw IllegalStateException("Voice messages must include a positive duration.")
        }
        if (durationMs > MAX_DURATION_MS) {
            throw IllegalStateException("Voice messages are limited to 60 seconds.")
        }
        if (byteCount <= 0) {
            throw IllegalStateException("The voice request did not include any audio.")
        }
        if (byteCount > MAX_BYTE_COUNT) {
            throw IllegalStateException("Voice messages are limited to 10 MB.")
        }
    }
}

class VoiceTranscriptionAuthExpiredException :
    IllegalStateException("Your ChatGPT login has expired. Sign in again.")

object GPTVoiceTranscriptionClient {
    private const val TAG = "GPTVoiceTranscription"
    private const val TRANSCRIPTION_URL = "https://chatgpt.com/backend-api/transcribe"
    private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()

    suspend fun transcribe(
        okHttpClient: OkHttpClient,
        json: Json,
        wavData: ByteArray,
        token: String
    ): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "voice.wav",
                wavData.toRequestBody(WAV_MEDIA_TYPE)
            )
            .build()

        val request = Request.Builder()
            .url(TRANSCRIPTION_URL)
            .post(body)
            .header("Authorization", "Bearer $token")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            Log.d(TAG, "Transcription HTTP ${response.code}, bodyLength=${rawBody.length}")
            when {
                response.code == 401 || response.code == 403 -> throw VoiceTranscriptionAuthExpiredException()
                !response.isSuccessful -> {
                    val serverMessage = extractErrorMessage(json, rawBody)
                    Log.w(TAG, "Transcription failed with HTTP ${response.code}: ${serverMessage ?: rawBody.take(160)}")
                    throw IllegalStateException(
                        serverMessage ?: "Voice transcription failed (${response.code})."
                    )
                }
                else -> decodeTranscriptText(json, rawBody).also { transcript ->
                    Log.d(TAG, "Decoded transcript length=${transcript.length}")
                }
            }
        }
    }

    private fun extractErrorMessage(json: Json, rawBody: String): String? {
        val payload = rawBody.trim().takeIf { it.isNotEmpty() } ?: return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val nestedErrorMessage = root["error"]
            ?.jsonObject
            ?.get("message")
            .jsonStringOrNull()
        if (nestedErrorMessage != null) {
            return nestedErrorMessage
        }
        return root["message"].jsonStringOrNull()
    }

    private fun decodeTranscriptText(json: Json, rawBody: String): String {
        val payload = rawBody.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Transcript response was empty.")
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: run {
                Log.w(TAG, "Transcript response was not JSON: ${payload.take(160)}")
                throw IllegalStateException("Could not parse transcript response.")
            }

        listOf("text", "transcript").forEach { key ->
            val value = root[key].jsonStringOrNull()
            if (value != null) {
                return value
            }
        }

        Log.w(TAG, "Transcript JSON missing text fields: ${payload.take(160)}")
        throw IllegalStateException("Transcript response was empty.")
    }

    private fun JsonElement?.jsonStringOrNull(): String? {
        val primitive = runCatching { this?.jsonPrimitive }.getOrNull() ?: return null
        val content = runCatching { primitive.content }.getOrNull()?.trim() ?: return null
        return content.takeIf { it.isNotEmpty() }
    }
}
