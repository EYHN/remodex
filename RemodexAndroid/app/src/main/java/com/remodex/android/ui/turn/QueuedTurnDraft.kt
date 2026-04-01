package com.remodex.android.ui.turn

import com.remodex.android.data.model.CodexCollaborationModeKind
import com.remodex.android.data.model.CodexImageAttachment
import com.remodex.android.data.model.CodexTurnSkillMention
import java.util.UUID

data class QueuedTurnDraft(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val attachments: List<CodexImageAttachment> = emptyList(),
    val skillMentions: List<CodexTurnSkillMention> = emptyList(),
    val collaborationMode: CodexCollaborationModeKind? = null,
    val createdAt: Long = System.currentTimeMillis()
)
