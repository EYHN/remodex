package com.remodex.android.ui.turn

import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.data.model.CodexServiceTier
import com.remodex.android.ui.main.ContentViewModel

/**
 * Centralizes the composer runtime selection callbacks shared across nested views.
 */
data class TurnComposerRuntimeActions(
    val onSelectModel: (String?) -> Unit,
    val onSelectReasoningEffort: (String?) -> Unit,
    val onSelectServiceTier: (CodexServiceTier?) -> Unit,
    val onSelectAccessMode: (CodexAccessMode) -> Unit
) {
    companion object {
        fun fromViewModel(viewModel: ContentViewModel): TurnComposerRuntimeActions {
            return TurnComposerRuntimeActions(
                onSelectModel = viewModel::setSelectedModel,
                onSelectReasoningEffort = viewModel::setSelectedReasoningEffort,
                onSelectServiceTier = viewModel::setSelectedServiceTier,
                onSelectAccessMode = viewModel::setAccessMode
            )
        }
    }
}
