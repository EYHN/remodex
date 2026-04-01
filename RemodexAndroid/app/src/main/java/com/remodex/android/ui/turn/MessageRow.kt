package com.remodex.android.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.AssistantRevertPresentation
import com.remodex.android.data.model.CodexImageAttachment
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexMessageDeliveryState
import com.remodex.android.data.model.CodexMessageKind
import com.remodex.android.data.model.CodexMessageRole
import com.remodex.android.data.model.CodexPlanStepStatus
import com.remodex.android.data.model.JsonValue
import com.remodex.android.ui.theme.AccentBlue
import com.remodex.android.ui.theme.AccentPlan
import com.remodex.android.ui.theme.RemodexGray400
import com.remodex.android.ui.theme.StatusGreen
import com.remodex.android.ui.theme.StatusRed
import io.noties.markwon.Markwon
import kotlin.math.roundToInt

@Composable
fun MessageRow(
    message: CodexMessage,
    assistantRevertPresentation: AssistantRevertPresentation? = null,
    isAssistantDiffAvailable: Boolean = false,
    onOpenAssistantDiff: (() -> Unit)? = null,
    onOpenAssistantRevert: (() -> Unit)? = null,
    onSubmitStructuredUserInput: ((JsonValue, Map<String, List<String>>) -> Unit)? = null,
    onOpenSubagentThread: ((String) -> Unit)? = null,
    onRetryUserMessage: ((String, List<CodexImageAttachment>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (message.role) {
        CodexMessageRole.USER -> UserMessageRow(
            message = message,
            onRetryUserMessage = onRetryUserMessage,
            modifier = modifier
        )
        CodexMessageRole.ASSISTANT -> AssistantMessageRow(
            message = message,
            assistantRevertPresentation = assistantRevertPresentation,
            isAssistantDiffAvailable = isAssistantDiffAvailable,
            onOpenAssistantDiff = onOpenAssistantDiff,
            onOpenAssistantRevert = onOpenAssistantRevert,
            modifier = modifier
        )
        CodexMessageRole.SYSTEM -> SystemMessageRow(
            message = message,
            onSubmitStructuredUserInput = onSubmitStructuredUserInput,
            onOpenSubagentThread = onOpenSubagentThread,
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageRow(
    message: CodexMessage,
    onRetryUserMessage: ((String, List<CodexImageAttachment>) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var previewAttachment by remember { mutableStateOf<CodexImageAttachment?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (message.attachments.isNotEmpty()) {
            AttachmentThumbnailStrip(
                attachments = message.attachments,
                modifier = Modifier.padding(bottom = 6.dp),
                tileSize = 72.dp,
                onOpen = { previewAttachment = it }
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                when (message.deliveryState) {
                    CodexMessageDeliveryState.PENDING -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sending...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                        )
                    }
                    CodexMessageDeliveryState.FAILED -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Failed to send",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusRed
                        )
                    }
                    else -> Unit
                }
            }
        }

        if (message.text.isNotBlank() || message.attachments.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.text)) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (message.deliveryState == CodexMessageDeliveryState.FAILED && onRetryUserMessage != null) {
                    IconButton(
                        onClick = { onRetryUserMessage(message.text, message.attachments) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        previewAttachment?.let { attachment ->
            AttachmentPreviewDialog(
                attachment = attachment,
                onDismiss = { previewAttachment = null }
            )
        }
    }
}

@Composable
private fun AssistantMessageRow(
    message: CodexMessage,
    assistantRevertPresentation: AssistantRevertPresentation?,
    isAssistantDiffAvailable: Boolean,
    onOpenAssistantDiff: (() -> Unit)?,
    onOpenAssistantRevert: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val bodyColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val codeCommentContent = remember(message.text) {
        CodeCommentDirectiveParser.parse(message.text)
    }
    var previewAttachment by remember { mutableStateOf<CodexImageAttachment?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
            ),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                if (message.attachments.isNotEmpty()) {
                    AttachmentThumbnailStrip(
                        attachments = message.attachments,
                        modifier = Modifier.padding(bottom = 8.dp),
                        tileSize = 72.dp,
                        onOpen = { previewAttachment = it }
                    )
                }

                if (codeCommentContent.hasFindings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (codeCommentContent.fallbackText.isBlank()) 0.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        codeCommentContent.findings.forEach { finding ->
                            CodeCommentDirectiveFindingCard(finding = finding)
                        }
                    }
                }

                if (codeCommentContent.fallbackText.isNotBlank()) {
                    AssistantMarkdownContent(
                        messageId = message.id,
                        text = codeCommentContent.fallbackText,
                        markwon = markwon,
                        bodyColor = bodyColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Streaming…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!message.isStreaming && message.text.isNotBlank()) {
            Row(
                modifier = Modifier.zIndex(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.text)) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAssistantDiffAvailable && onOpenAssistantDiff != null) {
                    TextButton(onClick = onOpenAssistantDiff) {
                        Text("Diff")
                    }
                }

                if (assistantRevertPresentation != null && onOpenAssistantRevert != null) {
                    TextButton(
                        onClick = onOpenAssistantRevert,
                        enabled = assistantRevertPresentation.isEnabled
                    ) {
                        Text(assistantRevertPresentation.title)
                    }
                }
            }
        }

        previewAttachment?.let { attachment ->
            AttachmentPreviewDialog(
                attachment = attachment,
                onDismiss = { previewAttachment = null }
            )
        }
    }
}

@Composable
private fun CodeCommentDirectiveFindingCard(
    finding: CodeCommentDirectiveFinding
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AccentBlue
                )
                Text(
                    text = finding.priority?.let { "[P$it] ${finding.title}" } ?: finding.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = buildString {
                    append(finding.file)
                    finding.startLine?.let { start ->
                        append(":")
                        append(start)
                        val end = finding.endLine
                        if (end != null && end != start) {
                            append("-")
                            append(end)
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = finding.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            finding.confidence?.let { confidence ->
                Text(
                    text = "Confidence ${(confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemMessageRow(
    message: CodexMessage,
    onSubmitStructuredUserInput: ((JsonValue, Map<String, List<String>>) -> Unit)?,
    onOpenSubagentThread: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    when (message.kind) {
        CodexMessageKind.THINKING -> ThinkingRow(message, modifier)
        CodexMessageKind.FILE_CHANGE -> FileChangeRow(message, modifier)
        CodexMessageKind.COMMAND_EXECUTION -> CommandExecutionCard(
            command = message.text,
            details = message.commandDetails,
            modifier = modifier
        )
        CodexMessageKind.TOOL_ACTIVITY -> ToolActivityRow(message, modifier)
        CodexMessageKind.SUBAGENT_ACTION -> SubagentRow(
            message = message,
            onOpenSubagentThread = onOpenSubagentThread,
            modifier = modifier
        )
        CodexMessageKind.PLAN -> PlanRow(message, modifier)
        CodexMessageKind.USER_INPUT_PROMPT -> {
            val request = message.structuredUserInputRequest
            if (request != null && onSubmitStructuredUserInput != null) {
                StructuredUserInputCard(
                    request = request,
                    onSubmit = onSubmitStructuredUserInput,
                    modifier = modifier
                )
            } else if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(vertical = 2.dp)
                )
            }
        }
        else -> {
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ThinkingRow(
    message: CodexMessage,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(message.id) { mutableStateOf(false) }
    val parsed = remember(message.text) {
        ThinkingDisclosureParser.parse(message.text)
    }
    val normalizedText = remember(message.text) {
        ThinkingDisclosureParser.normalizedThinkingContent(message.text)
    }
    val compactActivityPreview = remember(normalizedText) {
        ThinkingDisclosureParser.compactActivityPreview(normalizedText)
    }
    val displayText = compactActivityPreview ?: parsed.fallbackText
    val canExpand = parsed.showsDisclosure || displayText.length > 180 || displayText.contains('\n')

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canExpand) {
                    Modifier.clickable { isExpanded = !isExpanded }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AccentPlan
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (parsed.showsDisclosure) {
                    parsed.sections.forEachIndexed { index, section ->
                        if (!isExpanded && index > 0) {
                            return@forEachIndexed
                        }
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        section.detail
                            .trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                    }
                } else {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded || !canExpand) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FileChangeRow(
    message: CodexMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = AccentBlue
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun ToolActivityRow(
    message: CodexMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SubagentRow(
    message: CodexMessage,
    onOpenSubagentThread: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val action = message.subagentAction ?: return

    SubagentActionCard(
        action = action,
        isStreaming = message.isStreaming,
        threads = emptyList(),
        runningThreadIDs = emptySet(),
        onOpenSubagentThread = onOpenSubagentThread,
        modifier = modifier
    )
}

@Composable
private fun PlanRow(
    message: CodexMessage,
    modifier: Modifier = Modifier
) {
    val plan = message.planState
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AccentPlan.copy(alpha = 0.06f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            plan?.explanation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            plan?.steps?.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (step.status) {
                        CodexPlanStepStatus.COMPLETED -> Icons.Default.CheckCircle
                        CodexPlanStepStatus.IN_PROGRESS -> Icons.Default.RadioButtonChecked
                        CodexPlanStepStatus.PENDING -> Icons.Default.RadioButtonUnchecked
                    }
                    val color = when (step.status) {
                        CodexPlanStepStatus.COMPLETED -> StatusGreen
                        CodexPlanStepStatus.IN_PROGRESS -> AccentBlue
                        CodexPlanStepStatus.PENDING -> RemodexGray400
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (plan == null) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
