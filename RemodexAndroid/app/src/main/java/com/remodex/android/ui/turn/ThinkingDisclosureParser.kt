package com.remodex.android.ui.turn

data class ThinkingDisclosureSection(
    val id: String,
    val title: String,
    val detail: String
)

data class ThinkingDisclosureContent(
    val sections: List<ThinkingDisclosureSection>,
    val fallbackText: String
) {
    val showsDisclosure: Boolean get() = sections.isNotEmpty()
}

object ThinkingDisclosureParser {
    private val summaryRegex = Regex("^\\s*\\*\\*(.+?)\\*\\*\\s*$")
    private val compactActivityPrefixes = listOf(
        "running ",
        "completed ",
        "failed ",
        "stopped ",
        "read ",
        "search ",
        "searched ",
        "exploring ",
        "list ",
        "listing ",
        "open ",
        "opened ",
        "find ",
        "finding ",
        "edit ",
        "edited ",
        "write ",
        "wrote ",
        "apply ",
        "applied "
    )

    fun parse(rawText: String): ThinkingDisclosureContent {
        val normalizedText = normalizedThinkingContent(rawText)
        if (normalizedText.isBlank()) {
            return ThinkingDisclosureContent(sections = emptyList(), fallbackText = "")
        }

        val lines = normalizedText.split('\n')
        val preambleLines = mutableListOf<String>()
        val currentDetailLines = mutableListOf<String>()
        val sections = mutableListOf<ThinkingDisclosureSection>()
        var currentTitle: String? = null

        fun flushCurrentSection() {
            val title = currentTitle ?: return
            sections += ThinkingDisclosureSection(
                id = "${sections.size}-$title",
                title = title,
                detail = joinBlock(currentDetailLines)
            )
            currentDetailLines.clear()
        }

        lines.forEach { line ->
            val summaryTitle = summaryTitle(line)
            if (summaryTitle != null) {
                flushCurrentSection()
                currentTitle = summaryTitle
            } else if (currentTitle == null) {
                preambleLines += line
            } else {
                currentDetailLines += line
            }
        }
        flushCurrentSection()

        if (sections.isNotEmpty()) {
            val preamble = joinBlock(preambleLines)
            val merged = sections.toMutableList()
            if (preamble.isNotBlank()) {
                val first = merged.removeFirst()
                merged.add(
                    0,
                    first.copy(
                        detail = listOf(preamble, first.detail)
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n\n")
                    )
                )
            }
            return ThinkingDisclosureContent(
                sections = coalesceAdjacentSections(merged),
                fallbackText = normalizedText
            )
        }

        return ThinkingDisclosureContent(sections = emptyList(), fallbackText = normalizedText)
    }

    fun normalizedThinkingContent(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return ""
        }

        val lower = trimmed.lowercase()
        if (lower == "thinking") {
            return ""
        }
        if (lower.startsWith("thinking...")) {
            return trimmed.removePrefix(trimmed.take(11)).trim()
        }
        return trimmed
    }

    fun compactActivityPreview(normalizedText: String): String? {
        val lines = normalizedText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return null
        }

        val activityLines = lines.filter { line ->
            val lower = line.lowercase()
            compactActivityPrefixes.any(lower::startsWith)
        }

        if (activityLines.size == lines.size) {
            return activityLines.lastOrNull()
        }

        return activityLines.singleOrNull()
    }

    private fun summaryTitle(line: String): String? =
        summaryRegex.matchEntire(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun joinBlock(lines: List<String>): String =
        lines.joinToString(separator = "\n").trim()

    private fun coalesceAdjacentSections(
        sections: List<ThinkingDisclosureSection>
    ): List<ThinkingDisclosureSection> {
        val collapsed = mutableListOf<ThinkingDisclosureSection>()
        sections.forEach { section ->
            val previous = collapsed.lastOrNull()
            if (previous == null || previous.title != section.title) {
                collapsed += section
                return@forEach
            }

            val mergedDetail = when {
                previous.detail == section.detail || section.detail.isBlank() -> previous.detail
                previous.detail.isBlank() || section.detail.contains(previous.detail) -> section.detail
                previous.detail.contains(section.detail) -> previous.detail
                else -> listOf(previous.detail, section.detail)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "\n\n")
            }
            collapsed[collapsed.lastIndex] = previous.copy(detail = mergedDetail)
        }
        return collapsed
    }
}
