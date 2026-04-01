package com.remodex.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CodexThreadRuntimeOverride(
    val reasoningEffort: String? = null,
    val serviceTierRawValue: String? = null,
    val overridesReasoning: Boolean = false,
    val overridesServiceTier: Boolean = false
) {
    val serviceTier: CodexServiceTier?
        get() = serviceTierRawValue?.let { raw ->
            CodexServiceTier.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }

    val isEmpty: Boolean
        get() = !overridesReasoning && !overridesServiceTier

    /** Produces a new override by layering per-thread values over global defaults. */
    fun mergedWith(
        globalReasoningEffort: String?,
        globalServiceTier: CodexServiceTier?
    ): ResolvedRuntimeConfig {
        val effectiveReasoning = if (overridesReasoning && reasoningEffort != null) {
            reasoningEffort
        } else {
            globalReasoningEffort
        }
        val effectiveTier = if (overridesServiceTier) {
            serviceTier
        } else {
            globalServiceTier
        }
        return ResolvedRuntimeConfig(
            reasoningEffort = effectiveReasoning,
            serviceTier = effectiveTier
        )
    }

    companion object {
        val EMPTY = CodexThreadRuntimeOverride()

        fun withReasoningEffort(effort: String): CodexThreadRuntimeOverride =
            CodexThreadRuntimeOverride(
                reasoningEffort = effort,
                overridesReasoning = true
            )

        fun withServiceTier(tier: CodexServiceTier): CodexThreadRuntimeOverride =
            CodexThreadRuntimeOverride(
                serviceTierRawValue = tier.name.lowercase(),
                overridesServiceTier = true
            )
    }
}

data class ResolvedRuntimeConfig(
    val reasoningEffort: String?,
    val serviceTier: CodexServiceTier?
)
