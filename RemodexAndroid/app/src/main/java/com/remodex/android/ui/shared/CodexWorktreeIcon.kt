package com.remodex.android.ui.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fork / git-branch icon used in sidebar rows and menus to indicate forked threads.
 */
@Composable
fun CodexForkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = Icons.Default.CallSplit,
        contentDescription = "Fork",
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * Worktree icon (rotated branch symbol) shown in sidebar and turn screens
 * to indicate worktree-backed threads.
 */
@Composable
fun CodexWorktreeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Icon(
        imageVector = Icons.Default.AccountTree,
        contentDescription = "Worktree",
        tint = tint,
        modifier = modifier
            .size(size)
            .rotate(90f),
    )
}

/**
 * Convenience row pairing a [CodexWorktreeIcon] with a text label,
 * suitable for menu items and list rows.
 */
@Composable
fun CodexWorktreeMenuLabelRow(
    title: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 13.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        CodexWorktreeIcon(size = iconSize, tint = tint)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title)
    }
}
