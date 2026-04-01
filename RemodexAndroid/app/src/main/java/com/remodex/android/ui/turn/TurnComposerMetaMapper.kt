package com.remodex.android.ui.turn

import com.remodex.android.data.model.CodexModelOption

/**
 * Centralizes model/reasoning label mapping and ordering for TurnComposer menus.
 */
object TurnComposerMetaMapper {

    // ── Model Mapping ───────────────────────────────────────────────────

    private val preferredModelOrder = listOf(
        "gpt-5.1-codex-mini",
        "gpt-5.2",
        "gpt-5.1-codex-max",
        "gpt-5.2-codex",
        "gpt-5.3-codex",
    )

    /**
     * Returns the display label for the currently selected model,
     * or "Auto" when no explicit selection exists.
     */
    fun modelDisplayLabel(
        availableModels: List<CodexModelOption>,
        selectedModelId: String?
    ): String {
        if (selectedModelId == null) return "Auto"
        return availableModels.firstOrNull { model ->
            model.id == selectedModelId || model.model == selectedModelId
        }?.let { modelTitle(it) } ?: "Auto"
    }

    /**
     * Normalizes backend model ids into consistent menu labels.
     */
    fun modelTitle(model: CodexModelOption): String {
        return when (model.model.lowercase()) {
            "gpt-5.3-codex" -> "GPT-5.3-Codex"
            "gpt-5.2-codex" -> "GPT-5.2-Codex"
            "gpt-5.1-codex-max" -> "GPT-5.1-Codex-Max"
            "gpt-5.4" -> "GPT-5.4"
            "gpt-5.2" -> "GPT-5.2"
            "gpt-5.1-codex-mini" -> "GPT-5.1-Codex-Mini"
            else -> model.label
        }
    }

    /**
     * Returns models sorted by the explicit product order expected by the UI.
     */
    fun orderedModels(models: List<CodexModelOption>): List<CodexModelOption> {
        val rankByModel = preferredModelOrder
            .withIndex()
            .associate { (index, value) -> value to index }

        return models.sortedWith(compareBy<CodexModelOption> {
            rankByModel[it.model.lowercase()] ?: Int.MAX_VALUE
        }.thenByDescending { modelTitle(it) })
    }

    // ── Reasoning Mapping ───────────────────────────────────────────────

    /**
     * Maps a raw reasoning effort value to a user-facing label.
     * Returns "Auto" when the effort is null (automatic selection).
     */
    fun reasoningDisplayLabel(effort: String?): String {
        if (effort == null) return "Auto"
        return reasoningTitle(effort)
    }

    /**
     * Maps raw effort strings to user-facing labels.
     */
    fun reasoningTitle(effort: String): String {
        val normalized = effort.trim().lowercase()
        return when (normalized) {
            "minimal", "low" -> "Low"
            "medium" -> "Medium"
            "high" -> "High"
            "xhigh", "extra_high", "extra-high", "very_high", "very-high" -> "Extra High"
            else -> normalized
                .split("_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    /**
     * Returns reasoning display options sorted by level (high to low).
     */
    fun orderedReasoningEfforts(efforts: List<String>): List<ReasoningDisplayOption> {
        return efforts.map { effort ->
            ReasoningDisplayOption(effort = effort, title = reasoningTitle(effort))
        }.sortedByDescending { it.rank }
    }

    // ── Service Tier Mapping ────────────────────────────────────────────

    /**
     * Formats a menu label for a service tier row, appending " . current" when active.
     */
    fun tierMenuLabel(tier: com.remodex.android.data.model.CodexServiceTier, isSelected: Boolean): String {
        return if (isSelected) "${tier.displayLabel} \u2022 current" else tier.displayLabel
    }

    // ── Access Mode Mapping ─────────────────────────────────────────────

    /**
     * Formats a menu label for an access mode row.
     */
    fun accessModeDisplayLabel(mode: com.remodex.android.data.model.CodexAccessMode): String {
        return mode.displayLabel
    }
}

/**
 * A reasoning effort paired with its display title and sort rank.
 */
data class ReasoningDisplayOption(
    val effort: String,
    val title: String
) {
    val rank: Int
        get() = when (title) {
            "Low" -> 0
            "Medium" -> 1
            "High" -> 2
            "Extra High" -> 3
            else -> 4
        }
}
