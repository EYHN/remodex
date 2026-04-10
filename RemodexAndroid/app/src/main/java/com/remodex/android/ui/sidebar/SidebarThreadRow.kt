package com.remodex.android.ui.sidebar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexThread
import com.remodex.android.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarThreadRow(
    thread: CodexThread,
    isSelected: Boolean,
    isRunning: Boolean,
    isReady: Boolean,
    isFailed: Boolean,
    indentLevel: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleChildren: (() -> Unit)? = null,
    onClick: () -> Unit,
    onRenameThread: (String) -> Unit,
    onArchiveThread: () -> Unit,
    onDeleteThread: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    // iOS-style: flat rows with only a subtle selected background, no borders
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var pendingTitle by remember(thread.displayTitle) { mutableStateOf(thread.displayTitle) }
    val titleColor = MaterialTheme.colorScheme.onSurface
    val timestampColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { isMenuOpen = true }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (level in 0 until indentLevel.coerceAtLeast(0)) {
                Spacer(modifier = Modifier.width(14.dp))
            }

            if (hasChildren) {
                IconButton(
                    onClick = { onToggleChildren?.invoke() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.ExpandMore
                        } else {
                            Icons.Default.ChevronRight
                        },
                        contentDescription = if (isExpanded) "Collapse subagent threads" else "Expand subagent threads",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else if (indentLevel > 0) {
                Spacer(modifier = Modifier.width(28.dp))
            }

            // iOS-style: blue dot for running, no dot for idle threads
            val badgeColor = when {
                isRunning -> StatusBlue
                isReady -> StatusGreen
                isFailed -> StatusRed
                else -> null
            }
            if (badgeColor != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // iOS-style: single-line title only, no preview subtitle
            Text(
                text = thread.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // iOS-style: right-aligned relative time
            Text(
                text = formatRelativeTime(thread.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = timestampColor
            )

            // Context menu triggered by long press (iOS-style), no visible "..." button
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    },
                    onClick = {
                        pendingTitle = thread.displayTitle
                        isRenameDialogOpen = true
                        isMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Archive") },
                    leadingIcon = {
                        Icon(Icons.Default.Archive, contentDescription = null)
                    },
                    onClick = {
                        onArchiveThread()
                        isMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        onDeleteThread()
                        isMenuOpen = false
                    }
                )
            }
        }
    }

    if (isRenameDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRenameDialogOpen = false },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = pendingTitle,
                    onValueChange = { pendingTitle = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Conversation title") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = pendingTitle.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameThread(trimmed)
                        }
                        isRenameDialogOpen = false
                    },
                    enabled = pendingTitle.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenameDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun threadStatusIconSlot(thread: CodexThread) {
    Box(
        modifier = Modifier.size(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            thread.isForkedThread -> {
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = "Forked thread",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            thread.isManagedWorktreeProject -> {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = "Managed worktree",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHour = diffMin / 60
    val diffDay = diffHour / 24

    return when {
        diffSec < 60 -> "now"
        diffMin < 60 -> "${diffMin}m"
        diffHour < 24 -> "${diffHour}h"
        diffDay < 7 -> "${diffDay}d"
        else -> "${diffDay / 7}w"
    }
}
