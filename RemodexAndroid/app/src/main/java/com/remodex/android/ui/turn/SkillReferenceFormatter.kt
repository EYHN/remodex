package com.remodex.android.ui.turn

import java.net.URI

/**
 * Replaces skill file-path references with compact display names or mention tokens.
 *
 * Mirrors iOS `SkillReferenceFormatter`.
 */
object SkillReferenceFormatter {

    // Only paths under dedicated skill roots render as skills;
    // project files named Skill.md stay as normal file references.
    private val knownSkillPathMarkers = listOf(
        "/.codex/skills/",
        "/.agents/skills/",
    )

    // --- Regex patterns used only within this formatter -----------------------

    private val markdownLinkRangeRegex = Regex("""\[[^\]]+\]\([^)]+\)""")
    private val markdownLinkTokenRegex = Regex("""^\[([^\]]+)\]\(([^)]+)\)$""")
    private val inlineCodeContentRegex = Regex("""`([^`\n]+)`""")
    private val inlineCodeRangeRegex = Regex("""`[^`]+`""")
    private val genericPathRegex = Regex(
        """(?:/[^\s`"'<>]+|~/[^\s`"'<>]+|\.{1,2}/[^\s`"'<>]+|[A-Za-z0-9._+\-]+(?:/[A-Za-z0-9._+\-]+)+)(?::\d+(?::\d+)?)?"""
    )

    // --- Public API ----------------------------------------------------------

    fun replacingSkillReferences(
        text: String,
        style: SkillReferenceReplacementStyle,
    ): String {
        val lines = text.split("\n")
        var isInsideFence = false

        val transformed = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                isInsideFence = !isInsideFence
                return@map line
            }
            if (isInsideFence) {
                return@map line
            }
            replacingSkillReferencesInLine(line, style)
        }

        return transformed.joinToString("\n")
    }

    // --- Per-line replacement pipeline ---------------------------------------

    private fun replacingSkillReferencesInLine(
        line: String,
        style: SkillReferenceReplacementStyle,
    ): String {
        var result = replaceMarkdownSkillLinks(line, style)
        result = replaceInlineCodeSkillReferences(result, style)
        return replaceGenericSkillPaths(result, style)
    }

    /**
     * Replace skill paths that appear as markdown link destinations, e.g.
     * `[label](/home/.codex/skills/my-skill/Skill.md)`.
     */
    private fun replaceMarkdownSkillLinks(
        line: String,
        style: SkillReferenceReplacementStyle,
    ): String {
        val matches = markdownLinkRangeRegex.findAll(line).toList()
        if (matches.isEmpty()) return line

        val sb = StringBuilder(line)
        // Process in reverse so earlier indices stay valid.
        for (match in matches.asReversed()) {
            val token = match.value
            val skillName = skillName(token) ?: continue
            sb.replace(match.range.first, match.range.last + 1, replacementText(skillName, style))
        }
        return sb.toString()
    }

    /**
     * Replace skill paths wrapped in single back-ticks, e.g.
     * `` `/home/.codex/skills/my-skill/Skill.md` ``.
     */
    private fun replaceInlineCodeSkillReferences(
        line: String,
        style: SkillReferenceReplacementStyle,
    ): String {
        val matches = inlineCodeContentRegex.findAll(line).toList()
        if (matches.isEmpty()) return line

        val sb = StringBuilder(line)
        for (match in matches.asReversed()) {
            val innerGroup = match.groups[1] ?: continue
            val token = innerGroup.value
            val skillName = skillName(token) ?: continue
            // Replace the entire back-tick span (including the ticks).
            sb.replace(match.range.first, match.range.last + 1, replacementText(skillName, style))
        }
        return sb.toString()
    }

    /**
     * Replace bare skill paths that are not already inside markdown links or
     * inline code spans.
     */
    private fun replaceGenericSkillPaths(
        line: String,
        style: SkillReferenceReplacementStyle,
    ): String {
        val matches = genericPathRegex.findAll(line).toList()
        if (matches.isEmpty()) return line

        val linkRanges = markdownLinkRanges(line)
        val codeRanges = inlineCodeRanges(line)

        val sb = StringBuilder(line)
        for (match in matches.asReversed()) {
            val range = match.range
            if (rangeOverlaps(range, linkRanges)) continue
            if (rangeOverlaps(range, codeRanges)) continue

            val token = match.value
            val skillName = skillName(token) ?: continue
            sb.replace(range.first, range.last + 1, replacementText(skillName, style))
        }
        return sb.toString()
    }

    // --- Skill-name extraction -----------------------------------------------

    /**
     * Extracts the skill directory name from a raw reference string that points
     * to a recognised skill path (e.g. `/.codex/skills/my-skill/Skill.md`).
     * Returns `null` when the reference is not a skill path.
     */
    private fun skillName(rawReference: String): String? {
        val normalized = normalizedPath(rawReference)
        if (!isSkillPath(normalized)) return null

        val components = normalized.split("/").filter { it.isNotEmpty() }
        val skillsIndex = components.indexOf("skills")
        if (skillsIndex < 0 || skillsIndex + 1 >= components.size) return null

        val name = components[skillsIndex + 1].trim()
        return name.ifEmpty { null }
    }

    /**
     * Strips quoting, markdown link syntax, trailing punctuation, query strings,
     * and fragment identifiers to yield a clean file-system path.
     */
    private fun normalizedPath(rawReference: String): String {
        var candidate = rawReference.trim()
        candidate = candidate.trimStart('`', '"', '\'').trimEnd('`', '"', '\'')

        // If the token is a markdown link, extract the destination.
        parseMarkdownLink(candidate)?.let { (_, destination) ->
            candidate = destination
        }

        // Strip trailing punctuation that is not part of the path.
        while (candidate.isNotEmpty() && candidate.last() in ",.;)]}") {
            candidate = candidate.dropLast(1)
        }
        if (candidate.startsWith("(")) {
            candidate = candidate.drop(1)
        }

        // Remove query string and fragment.
        candidate.indexOf('?').takeIf { it >= 0 }?.let { candidate = candidate.substring(0, it) }
        candidate.indexOf('#').takeIf { it >= 0 }?.let { candidate = candidate.substring(0, it) }

        // If it parses as a URI, prefer the path component.
        try {
            val uri = URI(candidate)
            val path = uri.path
            if (!path.isNullOrEmpty()) return path
        } catch (_: Exception) {
            // Not a valid URI; fall through.
        }

        return candidate
    }

    private fun isSkillPath(normalizedPath: String): Boolean {
        val lower = normalizedPath.lowercase()
        if (!lower.endsWith("/skill.md")) return false
        return knownSkillPathMarkers.any { lower.contains(it) }
    }

    // --- Display formatting --------------------------------------------------

    private fun replacementText(
        skillName: String,
        style: SkillReferenceReplacementStyle,
    ): String = when (style) {
        SkillReferenceReplacementStyle.MentionToken -> "\$$skillName"
        SkillReferenceReplacementStyle.DisplayName -> displayName(skillName)
    }

    /**
     * Converts a slug name like `"skill-builder"` to `"Skill Builder"`.
     *
     * Equivalent to iOS `SkillDisplayNameFormatter.displayName(for:)`.
     */
    fun displayName(rawName: String): String {
        val normalized = rawName.trim()
        if (normalized.isEmpty()) return rawName

        val parts = normalized.split(Regex("[-_]+"))
            .filter { it.isNotEmpty() }
            .map { part ->
                part.first().uppercaseChar() + part.drop(1).lowercase()
            }

        return if (parts.isEmpty()) normalized else parts.joinToString(" ")
    }

    // --- Range helpers -------------------------------------------------------

    private fun parseMarkdownLink(token: String): Pair<String, String>? {
        val match = markdownLinkTokenRegex.matchEntire(token) ?: return null
        val label = match.groups[1]?.value ?: return null
        val destination = match.groups[2]?.value ?: return null
        return label to destination
    }

    private fun markdownLinkRanges(line: String): List<IntRange> =
        markdownLinkRangeRegex.findAll(line).map { it.range }.toList()

    private fun inlineCodeRanges(line: String): List<IntRange> =
        inlineCodeRangeRegex.findAll(line).map { it.range }.toList()

    private fun rangeOverlaps(range: IntRange, protectedRanges: List<IntRange>): Boolean =
        protectedRanges.any { protected ->
            range.first <= protected.last && protected.first <= range.last
        }
}
