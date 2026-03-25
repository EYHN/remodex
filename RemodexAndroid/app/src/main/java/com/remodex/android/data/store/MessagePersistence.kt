package com.remodex.android.data.store

import android.content.Context
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexMessageOrderCounter
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

class MessagePersistence(
    private val context: Context,
    private val json: Json
) {
    private val file: File get() = File(context.filesDir, "message_cache.json")
    private var saveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mapSerializer = MapSerializer(
        String.serializer(),
        ListSerializer(CodexMessage.serializer())
    )

    fun load(): Map<String, List<CodexMessage>> {
        return try {
            val text = file.readText()
            if (text.isBlank()) return emptyMap()
            val result = json.decodeFromString(mapSerializer, text)
            // Seed order counter from loaded messages
            var maxOrder = 0
            result.values.forEach { messages ->
                messages.forEach { msg ->
                    if (msg.orderIndex > maxOrder) maxOrder = msg.orderIndex
                }
            }
            CodexMessageOrderCounter.seed(maxOrder)
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveLater(messagesByThread: Map<String, List<CodexMessage>>) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(2000) // Debounce 2s
            save(messagesByThread)
        }
    }

    fun saveNow(messagesByThread: Map<String, List<CodexMessage>>) {
        scope.launch { save(messagesByThread) }
    }

    private fun save(messagesByThread: Map<String, List<CodexMessage>>) {
        try {
            val text = json.encodeToString(mapSerializer, messagesByThread)
            file.writeText(text)
        } catch (_: Exception) {}
    }
}
