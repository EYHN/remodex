package com.remodex.android.ui.turn

import com.remodex.android.data.model.AIChangeSet
import com.remodex.android.data.model.AIFileChange

// ---------------------------------------------------------------------------
// TurnFileChangeSummaryParser
//
// Parses assistant message text to extract per-file change summaries from
// structured recap prose (Path:/Kind:/Totals: blocks), inline action lines
// ("Edited foo.kt +5 -3"), and fenced diff blocks.
//
// Mirrors the iOS TurnFileChangeSummaryParser.
// ---------------------------------------------------------------------------

object TurnFileChangeSummaryParser {

    // -- Public types -------------------------------------------------------

    enum class FileChangeAction(val label: String) {
        EDITED("Edited"),
        ADDED("Added"),
        DELETED("Deleted"),
        RENAMED("Renamed");

        companion object {
            fun fromInlineVerb(verb: String): FileChangeAction? =
                when (verb.trim().lowercase()) {
                    "edited", "updated" -> EDITED
                    "added", "created" -> ADDED
                    "deleted", "removed" -> DELETED
                    "renamed", "moved" -> RENAMED
                    else -> null
                }

            fun fromKind(kind: String): FileChangeAction? =
                when (kind.trim().lowercase()) {
                    "add", "added", "create", "created" -> ADDED
                    "delete", "deleted", "remove", "removed" -> DELETED
                    "rename", "renamed", "move", "moved" -> RENAMED
                    "update", "updated", "edit", "edited" -> EDITED
                    else -> null
                }
        }
    }

    data class FileChangeSummary(
        val path: String,
        val additions: Int,
        val deletions: Int,
        val action: FileChangeAction? = null,
    ) {
        val compactPath: String
            get() = path.substringAfterLast('/')
    }

    // -- Regex patterns (compiled once) ------------------------------------

    private val INLINE_ACTION = Regex(
        """(?i)^(edited|updated|added|created|deleted|removed|renamed|moved)\s+(.+?)$"""
    )
    private val INLINE_TOTALS = Regex(
        """[+\uFF0B]\s*(\d+)\s*[-\u2212\u2013\u2014\uFE63\uFF0D]\s*(\d+)"""
    )
    private val TRAILING_INLINE_TOTALS = Regex(
        """\s*[+\uFF0B]\s*\d+\s*[-\u2212\u2013\u2014\uFE63\uFF0D]\s*\d+\s*$"""
    )
    private val TRAILING_LINE_COLUMN = Regex(""":\d+(?::\d+)?$""")
    private val FILE_LIKE_TOKEN = Regex("""[A-Za-z0-9_+.\-]+\.[A-Za-z0-9]+$""")
    private val MARKDOWN_LINK = Regex("""^\[([^\]]+)]\(([^)]+)\)$""")
    private val INLINE_EDITING_ROW = Regex(
        """(?i)^(edited|updated|added|created|deleted|removed|renamed|moved)\s+.+\s+[+\uFF0B]\s*\d+\s*[-\u2212\u2013\u2014\uFE63\uFF0D]\s*\d+\s*$"""
    )
    private val COLLAPSIBLE_NEWLINES = Regex("""\n{3,}""")
    private val DIFF_METADATA_PREFIXES = listOf(
        "+++", "---", "diff --git", "@@", "index ",
        "\\ No newline", "new file mode", "deleted file mode",
        "similarity index", "rename from", "rename to",
    )

    // -- Main parse entry point --------------------------------------------

    fun parse(messageText: String): List<FileChangeSummary>? {
        val lines = messageText.split("\n")
        var lineIndex = 0
        var currentPath: String? = null
        val orderedPaths = mutableListOf<String>()
        val totalsByPath = mutableMapOf<String, IntArray>() // [additions, deletions]
        val kindsByPath = mutableMapOf<String, String>()
        val actionsByPath = mutableMapOf<String, FileChangeAction>()
        val pathsWithInlineTotals = mutableSetOf<String>()
        val pathsWithNonZeroInlineTotals = mutableSetOf<String>()
        val pathsWithDiffBodyEvidence = mutableSetOf<String>()

        var sawPathLine = false
        var sawKindLine = false
        var sawDiffFence = false
        var sawInlineTotals = false
        var sawInlineAction = false

        fun ensurePath(path: String) {
            if (path !in totalsByPath) {
                totalsByPath[path] = intArrayOf(0, 0)
                orderedPaths += path
            }
        }

        fun addTotals(path: String, add: Int, del: Int) {
            val arr = totalsByPath.getOrPut(path) {
                orderedPaths += path; intArrayOf(0, 0)
            }
            arr[0] += add
            arr[1] += del
        }

        while (lineIndex < lines.size) {
            val rawLine = lines[lineIndex]
            val trimmed = rawLine.trim()

            // Path: line
            parsePathLine(trimmed)?.let { path ->
                sawPathLine = true
                currentPath = path
                ensurePath(path)
                lineIndex++
                return@let
            } ?: run {

            // Kind: line
            if (currentPath != null) {
                parseKindLine(trimmed)?.let { kind ->
                    sawKindLine = true
                    kindsByPath[currentPath!!] = kind
                    if (currentPath!! !in actionsByPath) {
                        FileChangeAction.fromKind(kind)?.let { actionsByPath[currentPath!!] = it }
                    }
                    lineIndex++
                    return@run
                }

                // Totals: line
                parseTotalsLine(trimmed)?.let { (add, del) ->
                    val p = currentPath!!
                    if (add > 0 || del > 0) {
                        sawInlineTotals = true
                        pathsWithNonZeroInlineTotals += p
                    }
                    addTotals(p, add, del)
                    pathsWithInlineTotals += p
                    lineIndex++
                    return@run
                }
            }

            // Inline file entry ("Edited foo.kt +5 -3" or "foo.kt +5 -3")
            parseInlineFileEntry(trimmed)?.let { (path, inlineTotals, action) ->
                currentPath = path
                ensurePath(path)
                inlineTotals?.let { (add, del) ->
                    if (add > 0 || del > 0) {
                        sawInlineTotals = true
                        pathsWithNonZeroInlineTotals += path
                    }
                    addTotals(path, add, del)
                    pathsWithInlineTotals += path
                }
                action?.let {
                    sawInlineAction = true
                    actionsByPath[path] = it
                }
                lineIndex++
                return@run
            }

            // Fenced code block (may contain a diff)
            if (trimmed.startsWith("```")) {
                lineIndex++
                val codeLines = mutableListOf<String>()
                while (lineIndex < lines.size) {
                    if (lines[lineIndex].trim() == "```") break
                    codeLines += lines[lineIndex]
                    lineIndex++
                }

                if (detectVerifiedPatch(codeLines)) {
                    sawDiffFence = true
                    val resolvedPath = currentPath ?: parsePathFromDiff(codeLines)
                    if (resolvedPath != null && resolvedPath.isNotEmpty()) {
                        ensurePath(resolvedPath)
                        if (resolvedPath !in pathsWithInlineTotals) {
                            val (add, del) = countDiffLines(codeLines)
                            addTotals(resolvedPath, add, del)
                            if (add > 0 || del > 0) pathsWithDiffBodyEvidence += resolvedPath
                        } else {
                            val (add, del) = countDiffLines(codeLines)
                            if (add > 0 || del > 0) pathsWithDiffBodyEvidence += resolvedPath
                        }
                    }
                }

                // Skip closing fence
                if (lineIndex < lines.size) lineIndex++
                return@run
            }

            lineIndex++
            } // end run
        }

        val hasSignal = sawPathLine || sawKindLine || sawDiffFence || sawInlineTotals || sawInlineAction
        if (!hasSignal) return null

        val entries = orderedPaths.mapNotNull { path ->
            val arr = totalsByPath[path] ?: return@mapNotNull null
            val add = arr[0]
            val del = arr[1]
            val action = actionsByPath[path]
                ?: kindsByPath[path]?.let { FileChangeAction.fromKind(it) }
            val hasNonZero = add > 0 || del > 0
            val hasPatchEvidence = path in pathsWithDiffBodyEvidence || path in pathsWithNonZeroInlineTotals
            if (!hasNonZero && !(action != null && hasPatchEvidence)) return@mapNotNull null
            FileChangeSummary(path = path, additions = add, deletions = del, action = action)
        }

        val consolidated = consolidate(entries)
        return consolidated.ifEmpty { null }
    }

    /** Stable deduplication key for collapsing streaming / final duplicates. */
    fun dedupeKey(messageText: String): String? {
        val entries = parse(messageText) ?: return null
        if (entries.isEmpty()) return null
        return entries
            .sortedWith(compareBy({ it.path }, { it.action?.label ?: "" }, { it.additions }, { it.deletions }))
            .joinToString("||") { "${it.path}|${it.action?.label ?: ""}|+${it.additions}|-${it.deletions}" }
    }

    /** Strips inline editing recap rows so the caller can render them as dedicated UI blocks. */
    fun removingInlineEditingRows(text: String): String {
        val filtered = text.lines().filter { !isInlineEditingRow(it) }.joinToString("\n")
        return COLLAPSIBLE_NEWLINES.replace(filtered, "\n\n").trim()
    }

    // -- Structured-line parsers -------------------------------------------

    private fun parsePathLine(line: String): String? {
        if (!line.lowercase().startsWith("path:")) return null
        val value = line.drop(5).trim()
        if (value.isEmpty()) return null
        val normalized = normalizeInlinePath(value)
        return if (looksLikePath(normalized)) normalized else null
    }

    private fun parseKindLine(line: String): String? {
        if (!line.lowercase().startsWith("kind:")) return null
        val value = line.drop(5).trim()
        return value.ifEmpty { null }
    }

    private fun parseTotalsLine(line: String): Pair<Int, Int>? {
        if (!line.lowercase().startsWith("totals:")) return null
        return parseInlineTotals(line.drop(7).trim())
    }

    // -- Inline entry parser -----------------------------------------------

    private data class InlineEntry(
        val path: String,
        val inlineTotals: Pair<Int, Int>?,
        val action: FileChangeAction?,
    )

    private fun parseInlineFileEntry(line: String): InlineEntry? {
        var candidate = line
        if (candidate.startsWith("- ") || candidate.startsWith("* ")) {
            candidate = candidate.drop(2)
        } else if (candidate.startsWith("\u2022 ")) { // bullet
            candidate = candidate.drop(2)
        }
        candidate = candidate.replace("`", "")
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return null

        val totals = parseInlineTotals(trimmed)
        val withoutTotals = TRAILING_INLINE_TOTALS.replace(trimmed, "").trim()

        // Try "Verb path" pattern first
        INLINE_ACTION.find(withoutTotals)?.let { match ->
            val verb = match.groupValues[1]
            val rawPath = match.groupValues[2]
            val action = FileChangeAction.fromInlineVerb(verb) ?: return@let
            val normalized = normalizeInlinePath(rawPath)
            if (!looksLikePath(normalized)) return@let
            return InlineEntry(normalized, totals, action)
        }

        // Path-only rows need explicit +/- to avoid false positives
        if (totals == null) return null

        val firstToken = withoutTotals.split(" ", limit = 2).firstOrNull()?.trim() ?: withoutTotals
        val normalized = normalizeInlinePath(firstToken)
        if (!looksLikePath(normalized)) return null
        return InlineEntry(normalized, totals, null)
    }

    // -- Totals helpers ----------------------------------------------------

    private fun parseInlineTotals(text: String): Pair<Int, Int>? {
        val match = INLINE_TOTALS.find(text) ?: return null
        val add = match.groupValues[1].toIntOrNull() ?: return null
        val del = match.groupValues[2].toIntOrNull() ?: return null
        return add to del
    }

    // -- Path helpers ------------------------------------------------------

    private fun normalizeInlinePath(raw: String): String {
        var token = raw.trim().replace("\"", "").replace("'", "")

        // Markdown link
        MARKDOWN_LINK.find(token)?.let { match ->
            val dest = normalizeLinkDestination(match.groupValues[2])
            token = if (looksLikePath(dest)) dest else match.groupValues[1]
        }

        if (" " in token) {
            token = token.split(" ", limit = 2).first()
        }
        while (token.lastOrNull()?.let { it in ",.;)" } == true) token = token.dropLast(1)
        if (token.startsWith("(")) token = token.drop(1)
        token = TRAILING_LINE_COLUMN.replace(token, "")
        return token
    }

    private fun looksLikePath(token: String): Boolean {
        if (token.isEmpty()) return false
        if ("/" in token || token.startsWith("./") || token.startsWith("../")) return true
        return FILE_LIKE_TOKEN.containsMatchIn(token)
    }

    private fun normalizeLinkDestination(destination: String): String {
        var d = destination.trim()
        d.indexOf('?').takeIf { it >= 0 }?.let { d = d.substring(0, it) }
        d.indexOf('#').takeIf { it >= 0 }?.let { d = d.substring(0, it) }
        return d
    }

    // -- Diff helpers ------------------------------------------------------

    private fun detectVerifiedPatch(lines: List<String>): Boolean =
        lines.any { it.startsWith("diff --git ") || it.startsWith("--- ") || it.startsWith("+++ ") || it.startsWith("@@ ") }

    private fun parsePathFromDiff(lines: List<String>): String? {
        for (line in lines) {
            if (line.startsWith("+++ ")) {
                val c = normalizeDiffPath(line.removePrefix("+++ ").trim())
                if (c.isNotEmpty()) return c
            }
        }
        for (line in lines) {
            if (line.startsWith("diff --git ")) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val c = normalizeDiffPath(parts[3])
                    if (c.isNotEmpty()) return c
                }
            }
        }
        return null
    }

    private fun normalizeDiffPath(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "/dev/null") return ""
        return if (trimmed.startsWith("a/") || trimmed.startsWith("b/")) trimmed.drop(2) else trimmed
    }

    private fun countDiffLines(lines: List<String>): Pair<Int, Int> {
        var add = 0
        var del = 0
        for (line in lines) {
            if (line.isEmpty()) continue
            if (isDiffMetadata(line)) continue
            if (line.startsWith("+")) add++
            else if (line.startsWith("-")) del++
        }
        return add to del
    }

    private fun isDiffMetadata(line: String): Boolean =
        DIFF_METADATA_PREFIXES.any { line.startsWith(it) }

    private fun isInlineEditingRow(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        return INLINE_EDITING_ROW.matches(trimmed)
    }

    // -- Consolidation -----------------------------------------------------

    private fun consolidate(entries: List<FileChangeSummary>): List<FileChangeSummary> {
        val orderedPaths = mutableListOf<String>()
        val byPath = mutableMapOf<String, FileChangeSummary>()
        for (entry in entries) {
            val existing = byPath[entry.path]
            if (existing != null) {
                byPath[entry.path] = existing.copy(
                    additions = existing.additions + entry.additions,
                    deletions = existing.deletions + entry.deletions,
                    action = existing.action ?: entry.action,
                )
            } else {
                orderedPaths += entry.path
                byPath[entry.path] = entry
            }
        }
        return orderedPaths.mapNotNull { byPath[it] }
    }
}

// ---------------------------------------------------------------------------
// TurnSessionDiffSummary
//
// Computes per-session diff totals from AIChangeSet / AIFileChange lists.
// Mirrors the iOS TurnSessionDiffSummaryCalculator.
// ---------------------------------------------------------------------------

object TurnSessionDiffSummary {

    data class DiffTotals(
        val filesChanged: Int,
        val totalAdditions: Int,
        val totalDeletions: Int,
    ) {
        val hasChanges: Boolean get() = totalAdditions > 0 || totalDeletions > 0
    }

    /** Aggregate totals across every file change in every change set. */
    fun compute(changeSets: List<AIChangeSet>): DiffTotals =
        computeFromFileChanges(changeSets.flatMap { it.fileChanges })

    /** Aggregate totals from a flat list of file changes. */
    fun computeFromFileChanges(fileChanges: List<AIFileChange>): DiffTotals {
        var additions = 0
        var deletions = 0
        val paths = mutableSetOf<String>()
        for (fc in fileChanges) {
            additions += fc.additions
            deletions += fc.deletions
            if (fc.additions > 0 || fc.deletions > 0) paths += fc.path
        }
        return DiffTotals(
            filesChanged = paths.size,
            totalAdditions = additions,
            totalDeletions = deletions,
        )
    }
}
