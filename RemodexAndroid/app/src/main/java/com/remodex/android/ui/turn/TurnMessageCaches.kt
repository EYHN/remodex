package com.remodex.android.ui.turn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// ---------------------------------------------------------------------------
// Pre-compiled regex patterns shared across message rendering.
// Compiling once avoids repeated allocation on every recomposition.
// ---------------------------------------------------------------------------

object TurnMessageRegexCache {
    val CODE_FENCE = Regex("[\\s\\S]*?```", RegexOption.MULTILINE)
    val INLINE_CODE = Regex("`[^`]+`")
    val FILE_PATH = Regex("(?:^|\\s)(/[\\w./\\-]+(?:\\.[\\w]+)?)", RegexOption.MULTILINE)
    val DIFF_HEADER = Regex("^(?:---|\\+\\+\\+|@@)\\s", RegexOption.MULTILINE)
    val MERMAID_BLOCK = Regex("```mermaid\\s*\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    val THINKING_TAG = Regex("<thinking>([\\s\\S]*?)</thinking>", RegexOption.MULTILINE)
    val FILE_MENTION = Regex("@([\\w./\\-]+)")
    val SKILL_MENTION = Regex("\\$([\\w\\-]+)")
    val URL_PATTERN = Regex("https?://[^\\s)]+")
}

// ---------------------------------------------------------------------------
// Thread-safe LRU caches for expensive per-message parse results.
// Each cache is keyed by "$messageId:$contentHash" so stale entries are
// naturally evicted when message content changes.
// ---------------------------------------------------------------------------

class TurnMessageCaches {

    // -- Parsed markdown sections ----------------------------------------

    fun getCachedParsedSections(messageId: String, contentHash: Int): List<Any>? =
        parsedSections.get(cacheKey(messageId, contentHash))

    fun cacheParsedSections(messageId: String, contentHash: Int, sections: List<Any>) {
        parsedSections.put(cacheKey(messageId, contentHash), sections)
    }

    // -- File-change summaries -------------------------------------------

    data class FileChangeSummary(
        val filePath: String,
        val changeType: String,
        val additions: Int = 0,
        val deletions: Int = 0,
    )

    fun getCachedFileChanges(messageId: String, contentHash: Int): List<FileChangeSummary>? =
        fileChanges.get(cacheKey(messageId, contentHash))

    fun cacheFileChanges(messageId: String, contentHash: Int, changes: List<FileChangeSummary>) {
        fileChanges.put(cacheKey(messageId, contentHash), changes)
    }

    // -- Command-execution status ----------------------------------------

    fun getCachedCommandStatus(messageId: String, contentHash: Int): Boolean? =
        commandStatus.get(cacheKey(messageId, contentHash))

    fun cacheCommandStatus(messageId: String, contentHash: Int, hasCommand: Boolean) {
        commandStatus.put(cacheKey(messageId, contentHash), hasCommand)
    }

    // -- Housekeeping ----------------------------------------------------

    fun clear() {
        parsedSections.clear()
        fileChanges.clear()
        commandStatus.clear()
    }

    // -- Internals -------------------------------------------------------

    private fun cacheKey(messageId: String, contentHash: Int): String =
        "$messageId:$contentHash"

    private val parsedSections = LruMap<String, List<Any>>(MAX_ENTRIES)
    private val fileChanges = LruMap<String, List<FileChangeSummary>>(MAX_ENTRIES)
    private val commandStatus = LruMap<String, Boolean>(MAX_ENTRIES)

    companion object {
        private const val MAX_ENTRIES = 300
    }
}

// ---------------------------------------------------------------------------
// Minimal synchronized LRU map backed by LinkedHashMap(accessOrder = true).
// ---------------------------------------------------------------------------

private class LruMap<K, V>(private val maxEntries: Int) {

    private val map = object : LinkedHashMap<K, V>(
        /* initialCapacity = */ maxEntries + 1,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun clear() = map.clear()
}

// ---------------------------------------------------------------------------
// Compose helper -- survives recomposition for the lifetime of the caller.
// ---------------------------------------------------------------------------

@Composable
fun rememberTurnMessageCaches(): TurnMessageCaches = remember { TurnMessageCaches() }
