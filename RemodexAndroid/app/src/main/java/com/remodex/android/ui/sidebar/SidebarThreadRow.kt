package com.remodex.android.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexThread
import com.remodex.android.ui.theme.*

@Composable
fun SidebarThreadRow(
    thread: CodexThread,
    isSelected: Boolean,
    isRunning: Boolean,
    isReady: Boolean,
    isFailed: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status badge
        if (isRunning || isReady || isFailed) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRunning -> StatusBlue
                            isReady -> StatusGreen
                            isFailed -> StatusRed
                            else -> RemodexGray400
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Title and preview
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (thread.preview != null) {
                Text(
                    text = thread.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Relative time
        Text(
            text = formatRelativeTime(thread.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
