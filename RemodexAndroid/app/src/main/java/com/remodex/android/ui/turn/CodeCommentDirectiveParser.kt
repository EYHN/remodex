package com.remodex.android.ui.turn

data class CodeCommentDirectiveFinding(
    val id: String,
    val title: String,
    val body: String,
    val file: String,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val priority: Int? = null,
    val confidence: Double? = null
)

data class CodeCommentDirectiveContent(
    val findings: List<CodeCommentDirectiveFinding>,
    val fallbackText: String
) {
    val hasFindings: Boolean
        get() = findings.isNotEmpty()
}

object CodeCommentDirectiveParser {
    private val directiveRegex = Regex("""::code-comment\{((?:[^"\\}]|\\.|"([^"\\]|\\.)*")*)\}""")
    private val quotedAttributeRegex = Regex("""([A-Za-z][A-Za-z0-9_-]*)="((?:[^"\\]|\\.)*)"""")
    private val bareAttributeRegex = Regex("""([A-Za-z][A-Za-z0-9_-]*)=([^\s}]+)""")
    private val titlePriorityRegex = Regex("""^\s*\[(P\d+)\]\s*""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): CodeCommentDirectiveContent {
        val matches = directiveRegex.findAll(rawText).toList()
        if (matches.isEmpty()) {
            return CodeCommentDirectiveContent(emptyList(), rawText)
        }

        val findings = mutableListOf<CodeCommentDirectiveFinding>()
        val remaining = StringBuilder(rawText)

        for (match in matches.asReversed()) {
            val payload = match.groupValues.getOrNull(1).orEmpty()
            val finding = parseFinding(payload) ?: continue
            findings.add(0, finding)
            remaining.replace(match.range.first, match.range.last + 1, "")
        }

        return CodeCommentDirectiveContent(
            findings = findings,
            fallbackText = collapseDirectiveWhitespace(remaining.toString())
        )
    }

    private fun parseFinding(payload: String): CodeCommentDirectiveFinding? {
        val attributes = parseAttributes(payload)
        val rawTitle = attributes["title"]?.trim().orEmpty()
        val body = attributes["body"]?.trim().orEmpty()
        val file = attributes["file"]?.trim().orEmpty()
        if (rawTitle.isEmpty() || body.isEmpty() || file.isEmpty()) {
            return null
        }

        val normalizedTitle = stripPriorityPrefix(rawTitle).ifEmpty { rawTitle }
        val inferredPriority = inferPriority(rawTitle)
        val explicitPriority = attributes["priority"]?.toIntOrNull()
        val startLine = attributes["start"]?.toIntOrNull()
        val endLine = attributes["end"]?.toIntOrNull()
        val confidence = attributes["confidence"]?.toDoubleOrNull()

        return CodeCommentDirectiveFinding(
            id = "$file|${startLine ?: -1}|${endLine ?: -1}|$normalizedTitle",
            title = normalizedTitle,
            body = body,
            file = file,
            startLine = startLine,
            endLine = endLine,
            priority = explicitPriority ?: inferredPriority,
            confidence = confidence
        )
    }

    private fun parseAttributes(payload: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        val occupiedRanges = mutableListOf<IntRange>()

        quotedAttributeRegex.findAll(payload).forEach { match ->
            val key = match.groupValues.getOrNull(1).orEmpty()
            val value = match.groupValues.getOrNull(2)
                .orEmpty()
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            attributes[key] = value
            occupiedRanges += match.range
        }

        bareAttributeRegex.findAll(payload).forEach { match ->
            val range = match.range
            if (occupiedRanges.any { overlap -> range.first <= overlap.last && overlap.first <= range.last }) {
                return@forEach
            }

            val key = match.groupValues.getOrNull(1).orEmpty()
            val value = match.groupValues.getOrNull(2).orEmpty()
            attributes[key] = value
        }

        return attributes
    }

    private fun inferPriority(title: String): Int? {
        val match = titlePriorityRegex.find(title) ?: return null
        return match.groupValues.getOrNull(1)?.drop(1)?.toIntOrNull()
    }

    private fun stripPriorityPrefix(title: String): String {
        return title.replace(titlePriorityRegex, "").trim()
    }

    private fun collapseDirectiveWhitespace(text: String): String {
        val collapsedNewlines = text.replace(Regex("""\n{3,}"""), "\n\n")
        return collapsedNewlines
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }
}
