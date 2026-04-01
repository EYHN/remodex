package com.remodex.android.ui.turn

import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.data.model.CodexModelOption
import com.remodex.android.data.model.CodexServiceTier

/**
 * Bundles the composer runtime selection state shared by the bottom bar and input context menus.
 */
data class TurnComposerRuntimeState(
    val availableModels: List<CodexModelOption>,
    val selectedModelId: String?,
    val selectedModelTitle: String,
    val selectedReasoningEffort: String?,
    val supportedReasoningEfforts: List<String>,
    val selectedServiceTier: CodexServiceTier?,
    val selectedAccessMode: CodexAccessMode
) {
    val selectedReasoningLabel: String
        get() = TurnComposerMetaMapper.reasoningDisplayLabel(selectedReasoningEffort)

    val runtimeChipLabel: String
        get() = selectedServiceTier?.displayLabel ?: selectedReasoningLabel

    val reasoningMenuDisabled: Boolean
        get() = supportedReasoningEfforts.isEmpty()

    fun isSelectedModel(modelId: String?): Boolean = selectedModelId == modelId

    fun isSelectedReasoning(effort: String?): Boolean = selectedReasoningEffort == effort

    fun isSelectedServiceTier(tier: CodexServiceTier?): Boolean = selectedServiceTier == tier

    companion object {
        fun resolve(
            availableModels: List<CodexModelOption>,
            selectedModelId: String?,
            supportedReasoningEfforts: List<String>,
            selectedReasoningEffort: String?,
            selectedServiceTier: CodexServiceTier?,
            selectedAccessMode: CodexAccessMode
        ): TurnComposerRuntimeState {
            val title = TurnComposerMetaMapper.modelDisplayLabel(
                availableModels, selectedModelId
            )
            return TurnComposerRuntimeState(
                availableModels = availableModels,
                selectedModelId = selectedModelId,
                selectedModelTitle = title,
                selectedReasoningEffort = selectedReasoningEffort,
                supportedReasoningEfforts = supportedReasoningEfforts,
                selectedServiceTier = selectedServiceTier,
                selectedAccessMode = selectedAccessMode
            )
        }
    }
}

/**
 * Menu visibility state for the composer runtime dropdowns.
 * Kept separate so that selection state can be derived from the ViewModel
 * while visibility remains local composable state.
 */
data class TurnComposerMenuVisibility(
    val isModelMenuOpen: Boolean = false,
    val isRuntimeMenuOpen: Boolean = false,
    val isAccessMenuOpen: Boolean = false
)
