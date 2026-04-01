package com.remodex.android.ui.turn

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CommandExecutionDetails
import com.remodex.android.ui.theme.StatusGreen
import com.remodex.android.ui.theme.StatusRed

/**
 * Inline card showing command execution status: command text, output tail, and exit code.
 * Tapping opens [CommandExecutionDetailSheet] for full output.
 */
@Composable
fun CommandExecutionCard(
    command: String,
    details: CommandExecutionDetails?,
    modifier: Modifier = Modifier
) {
    var isSheetOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = details != null || command.isNotBlank()) {
                isSheetOpen = true
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (details != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommandStatusIcon(exitCode = details.exitCode)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$ ${details.fullCommand}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (details.outputTail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = details.outputTail,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                details.exitCode?.let { code ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Exit code: $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (code == 0) StatusGreen else StatusRed
                    )
                }
            } else {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (isSheetOpen) {
        CommandExecutionDetailSheet(
            command = command,
            details = details,
            onDismiss = { isSheetOpen = false }
        )
    }
}

/**
 * Small icon reflecting exit code status: green check for 0, red error for non-zero,
 * neutral terminal icon when exit code is not yet available.
 */
@Composable
private fun CommandStatusIcon(
    exitCode: Int?,
    modifier: Modifier = Modifier
) {
    when {
        exitCode == null -> Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = "Running",
            modifier = modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        exitCode == 0 -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Succeeded",
            modifier = modifier.size(16.dp),
            tint = StatusGreen
        )
        else -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Failed",
            modifier = modifier.size(16.dp),
            tint = StatusRed
        )
    }
}

/**
 * Bottom sheet for viewing full command output, working directory, and exit code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandExecutionDetailSheet(
    command: String,
    details: CommandExecutionDetails?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val displayCommand = details?.fullCommand?.takeIf { it.isNotBlank() } ?: command

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Command execution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        val copyText = buildString {
                            appendLine(displayCommand)
                            details?.outputTail?.takeIf { it.isNotBlank() }?.let {
                                appendLine()
                                append(it)
                            }
                        }
                        clipboard.setText(AnnotatedString(copyText))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Command
            Text(
                text = "$ $displayCommand",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Working directory
            details?.cwd?.takeIf { it.isNotBlank() }?.let { cwd ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = cwd,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Full output
            details?.outputTail?.takeIf { it.isNotBlank() }?.let { output ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            // Exit code
            details?.exitCode?.let { exitCode ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CommandStatusIcon(exitCode = exitCode)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exit code: $exitCode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (exitCode == 0) StatusGreen else StatusRed
                    )
                }
            }

            // Duration
            details?.durationMs?.let { ms ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String = when {
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "%.1fs".format(ms / 1_000.0)
    else -> {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        "${minutes}m ${seconds}s"
    }
}
