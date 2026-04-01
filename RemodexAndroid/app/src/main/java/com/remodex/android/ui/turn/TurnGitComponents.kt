package com.remodex.android.ui.turn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val TurnGitPillBackground = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.94f)
private val TurnGitPillBorder = androidx.compose.ui.graphics.Color(0x14000000)
private val TurnGitPillGray = androidx.compose.ui.graphics.Color(0xFF8E8E93)

/**
 * Compact branch-name chip that opens the branch selector sheet when tapped.
 * Shows a loading spinner while a branch switch is in progress.
 */
@Composable
fun TurnGitBranchSelector(
    currentBranch: String?,
    isLoading: Boolean,
    isSwitching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayBranch = currentBranch?.trim()?.takeIf { it.isNotEmpty() }

    Surface(
        modifier = modifier.clickable(enabled = enabled && !isSwitching, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = TurnGitPillBackground,
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            TurnGitPillBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSwitching || isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = TurnGitPillGray
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (enabled) {
                        TurnGitPillGray
                    } else {
                        TurnGitPillGray.copy(alpha = 0.38f)
                    }
                )
            }

            Text(
                text = when {
                    isSwitching -> "Switching..."
                    isLoading -> "Loading..."
                    displayBranch != null -> displayBranch
                    else -> "No branch"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) {
                    TurnGitPillGray
                } else {
                    TurnGitPillGray.copy(alpha = 0.38f)
                }
            )
        }
    }
}

/**
 * Horizontal toolbar with compact icon buttons for common git actions:
 * commit, push, pull, and repo diff.
 *
 * Buttons are disabled when the thread is running or the connection is down.
 */
@Composable
fun TurnGitActionsToolbar(
    isEnabled: Boolean,
    isRunning: Boolean,
    onCommit: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonsEnabled = isEnabled && !isRunning
    val enabledTint = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GitActionButton(
            icon = Icons.Default.PostAdd,
            contentDescription = "Commit",
            enabled = buttonsEnabled,
            enabledTint = enabledTint,
            disabledTint = disabledTint,
            onClick = onCommit
        )
        GitActionButton(
            icon = Icons.Default.Upload,
            contentDescription = "Push",
            enabled = buttonsEnabled,
            enabledTint = enabledTint,
            disabledTint = disabledTint,
            onClick = onPush
        )
        GitActionButton(
            icon = Icons.Default.Download,
            contentDescription = "Pull",
            enabled = buttonsEnabled,
            enabledTint = enabledTint,
            disabledTint = disabledTint,
            onClick = onPull
        )
        GitActionButton(
            icon = Icons.Default.CompareArrows,
            contentDescription = "Repo diff",
            enabled = buttonsEnabled,
            enabledTint = enabledTint,
            disabledTint = disabledTint,
            onClick = onDiff
        )
    }
}

@Composable
private fun GitActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    enabledTint: androidx.compose.ui.graphics.Color,
    disabledTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = enabledTint,
            disabledContentColor = disabledTint
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}
