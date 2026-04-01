package com.remodex.android.ui.turn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.remodex.android.data.model.AIFileChange
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexMessageKind
import com.remodex.android.data.model.CodexMessageRole
import com.remodex.android.data.model.CommandExecutionDetails

/**
 * Pre-computed render state for a single [CodexMessage].
 *
 * Heavy parsing (thinking disclosure, code-comment directives, code-block
 * detection) runs once when the message content changes and is reused across
 * Compose recompositions without re-executing regex work.
 */
data class MessageRowRenderModel(
    val messageId: String,
    val isThinkingBlock: Boolean,
    val hasCodeBlocks: Boolean,
    val parsedFileChanges: List<AIFileChange>?,
    val commandExecutionDetails: CommandExecutionDetails?,
    val thinkingDisclosure: ThinkingDisclosureContent?,
    val codeCommentDirectives: CodeCommentDirectiveContent,
    val contentHash: Int
)

// ---------------------------------------------------------------------------
// Cache
// ---------------------------------------------------------------------------

/**
 * Thread-safe LRU cache that maps message identity + content hash to a
 * pre-computed [MessageRowRenderModel].
 *
 * The cache key combines `message.id` and the hash code of `message.text`
 * so that streaming updates (which mutate `text` in place) naturally
 * invalidate stale entries.
 */
class MessageRenderCache(private val maxSize: Int = 200) {

    private val lock = Any()

    // LinkedHashMap with accessOrder=true gives us LRU eviction for free.
    private val map = object : LinkedHashMap<String, MessageRowRenderModel>(
        /* initialCapacity */ 64,
        /* loadFactor      */ 0.75f,
        /* accessOrder     */ true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, MessageRowRenderModel>?
        ): Boolean = size > maxSize
    }

    /** Return a cached model or compute, store, and return a fresh one. */
    fun getOrCompute(message: CodexMessage): MessageRowRenderModel {
        val key = cacheKey(message)
        synchronized(lock) {
            map[key]?.let { return it }
        }
        val model = compute(message)
        synchronized(lock) {
            map[key] = model
        }
        return model
    }

    /** Evict all entries. Useful on thread switch or memory pressure. */
    fun clear() {
        synchronized(lock) { map.clear() }
    }

    /** Current number of cached entries (mainly useful for tests). */
    val size: Int get() = synchronized(lock) { map.size }

    // -- internal helpers ---------------------------------------------------

    private fun cacheKey(message: CodexMessage): String =
        "${message.id}:${message.text.hashCode()}"

    private fun compute(message: CodexMessage): MessageRowRenderModel {
        val text = message.text

        val isThinking = message.role == CodexMessageRole.SYSTEM &&
            message.kind == CodexMessageKind.THINKING

        val thinkingDisclosure: ThinkingDisclosureContent? = if (isThinking) {
            ThinkingDisclosureParser.parse(text)
        } else {
            null
        }

        val codeCommentDirectives: CodeCommentDirectiveContent =
            if (message.role == CodexMessageRole.ASSISTANT) {
                CodeCommentDirectiveParser.parse(text)
            } else {
                CodeCommentDirectiveContent(findings = emptyList(), fallbackText = text)
            }

        val hasCodeBlocks = CODE_FENCE_REGEX.containsMatchIn(text)

        return MessageRowRenderModel(
            messageId = message.id,
            isThinkingBlock = isThinking,
            hasCodeBlocks = hasCodeBlocks,
            parsedFileChanges = null, // populated externally from AIChangeSetLedger
            commandExecutionDetails = message.commandDetails,
            thinkingDisclosure = thinkingDisclosure,
            codeCommentDirectives = codeCommentDirectives,
            contentHash = text.hashCode()
        )
    }

    private companion object {
        /** Matches fenced code blocks (``` with optional language tag). */
        val CODE_FENCE_REGEX = Regex("```")
    }
}

// ---------------------------------------------------------------------------
// Compose helper
// ---------------------------------------------------------------------------

/**
 * Remember a [MessageRenderCache] scoped to the current composition.
 *
 * The cache survives recompositions but is discarded when the composable
 * leaves the composition tree (e.g. navigating away from the turn screen).
 */
@Composable
fun rememberMessageRenderCache(): MessageRenderCache {
    return remember { MessageRenderCache() }
}
