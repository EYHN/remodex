package com.remodex.android.ui.turn

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.remodex.android.data.model.*
import com.remodex.android.ui.theme.*
import io.noties.markwon.Markwon

@Composable
fun MessageRow(message: CodexMessage) {
    when (message.role) {
        CodexMessageRole.USER -> UserMessageRow(message)
        CodexMessageRole.ASSISTANT -> AssistantMessageRow(message)
        CodexMessageRole.SYSTEM -> SystemMessageRow(message)
    }
}

@Composable
private fun UserMessageRow(message: CodexMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp, 10.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (message.deliveryState == CodexMessageDeliveryState.PENDING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sending...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                } else if (message.deliveryState == CodexMessageDeliveryState.FAILED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Failed to send",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusRed
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageRow(message: CodexMessage) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp, 10.dp)) {
                // Render markdown
                val markwon = remember(context) {
                    Markwon.create(context)
                }

                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            setTextColor(ctx.getColor(android.R.color.primary_text_light))
                            textSize = 14f
                            setLineSpacing(0f, 1.3f)
                        }
                    },
                    update = { tv ->
                        markwon.setMarkdown(tv, message.text)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Copy button
        if (!message.isStreaming && message.text.isNotBlank()) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.text)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemMessageRow(message: CodexMessage) {
    when (message.kind) {
        CodexMessageKind.THINKING -> ThinkingRow(message)
        CodexMessageKind.FILE_CHANGE -> FileChangeRow(message)
        CodexMessageKind.COMMAND_EXECUTION -> CommandExecutionRow(message)
        CodexMessageKind.TOOL_ACTIVITY -> ToolActivityRow(message)
        CodexMessageKind.SUBAGENT_ACTION -> SubagentRow(message)
        CodexMessageKind.PLAN -> PlanRow(message)
        else -> {
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ThinkingRow(message: CodexMessage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp, 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AccentPlan
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileChangeRow(message: CodexMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
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
private fun CommandExecutionRow(message: CodexMessage) {
    val details = message.commandDetails
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (details != null) {
                Text(
                    text = "$ ${details.fullCommand}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (details.outputTail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = details.outputTail,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                details.exitCode?.let { code ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Exit code: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code == 0) StatusGreen else StatusRed
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ToolActivityRow(message: CodexMessage) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Build,
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
private fun SubagentRow(message: CodexMessage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AccentPlan.copy(alpha = 0.08f)
    ) {
        Row(modifier = Modifier.padding(10.dp, 8.dp)) {
            Icon(
                Icons.Default.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = AccentPlan
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.subagentAction?.summaryText ?: message.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PlanRow(message: CodexMessage) {
    val plan = message.planState
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AccentPlan.copy(alpha = 0.06f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            plan?.explanation?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
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
                    Icon(icon, null, Modifier.size(16.dp), tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(step.step, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (plan == null) {
                Text(message.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
