package com.remodex.android.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.data.model.CodexCollaborationModeKind
import com.remodex.android.data.model.CodexFuzzyFileMatch
import com.remodex.android.data.model.CodexImageAttachment
import com.remodex.android.data.model.CodexModelOption
import com.remodex.android.data.model.CodexServiceTier
import com.remodex.android.data.model.CodexSkillMetadata
import com.remodex.android.data.model.CodexTurnSkillMention
import com.remodex.android.data.model.ContextWindowUsage
import com.remodex.android.ui.turn.TurnComposer
import com.remodex.android.ui.turn.TurnComposerStatusBar
import com.remodex.android.ui.turn.TurnTimeline
import com.remodex.android.ui.turn.TurnTopBar

@Composable
fun TurnExploreScreen() {
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    var selectedReasoningEffort by remember { mutableStateOf<String?>(null) }
    var selectedServiceTier by remember { mutableStateOf<CodexServiceTier?>(null) }
    var selectedAccessMode by remember { mutableStateOf(CodexAccessMode.ON_REQUEST) }
    var isComposerFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val shouldHideComposerStatusBar = isComposerFocused && isImeVisible

    val availableModels = remember {
        listOf(
            CodexModelOption(
                id = "gpt-5.4",
                model = "gpt-5.4",
                displayName = "GPT-5.4",
                isDefault = true,
                supportedReasoningEfforts = listOf("low", "medium", "high"),
                defaultReasoningEffort = "medium"
            )
        )
    }
    val selectedModelTitle = remember(availableModels, selectedModelId) {
        availableModels.firstOrNull { it.id == selectedModelId || it.model == selectedModelId }?.label ?: "GPT-5.4"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TurnTopBar(
            title = "Conversation",
            subtitle = "/Users/eyhn/remodex",
            onOpenSidebar = {},
            onSubtitleClick = {},
            onOpenMenu = {},
            onOpenStatus = {}
        )

        TurnTimeline(
            threadId = "android_explore_turn",
            messages = emptyList(),
            isRunning = false,
            modifier = Modifier.weight(1f)
        )

        TurnComposer(
            draftKey = "android_explore_turn",
            isRunning = false,
            isConnected = true,
            autocompleteRoot = "/Users/eyhn/remodex",
            availableModels = availableModels,
            selectedModelId = selectedModelId,
            selectedModelTitle = selectedModelTitle,
            selectedReasoningEffort = selectedReasoningEffort,
            selectedServiceTier = selectedServiceTier,
            supportedReasoningEfforts = listOf("low", "medium", "high"),
            onSelectModel = { selectedModelId = it },
            onSelectReasoningEffort = { selectedReasoningEffort = it },
            onSelectServiceTier = { selectedServiceTier = it },
            onFuzzyFileSearch = { _: String, _: List<String>, _: String? -> emptyList<CodexFuzzyFileMatch>() },
            onListSkills = { _: List<String>?, _: Boolean -> emptyList<CodexSkillMetadata>() },
            supportsReviewCommand = true,
            supportsForkCommand = true,
            onReviewAction = {},
            onForkAction = {},
            onShowStatus = {},
            onSteerQueuedDraft = { _: String, _: List<CodexImageAttachment>, _: List<CodexTurnSkillMention>, _: CodexCollaborationModeKind? -> },
            onTranscribeVoiceClip = { _: ByteArray, _: Long -> "" },
            onSend = { _: String, _: List<CodexImageAttachment>, _: List<CodexTurnSkillMention>, _: CodexCollaborationModeKind? -> },
            onStop = {},
            onInputFocusChanged = { isComposerFocused = it },
            modifier = Modifier.fillMaxWidth()
        )

        TurnComposerStatusBar(
            isComposerFocused = shouldHideComposerStatusBar,
            isWorktreeProject = false,
            isRuntimeSelectorEnabled = true,
            onOpenRuntimeActions = {},
            selectedAccessMode = selectedAccessMode,
            onSelectAccessMode = { selectedAccessMode = it },
            currentBranch = "android",
            isBranchSelectorEnabled = true,
            onOpenBranchSelector = {},
            contextWindowUsage = ContextWindowUsage(tokensUsed = 28_000, tokenLimit = 100_000),
            onOpenStatusSheet = {},
            modifier = Modifier.fillMaxWidth()
        )
    }
}
