package com.remodex.android.ui.turn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun VoiceRecordingCapsule(
    audioLevels: List<Float>,
    durationMs: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )

            VoiceWaveform(
                audioLevels = audioLevels,
                modifier = Modifier
                    .weight(1f)
                    .height(22.dp)
            )

            Text(
                text = formatVoiceDuration(durationMs),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel voice recording"
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveform(
    audioLevels: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val slotCount = maxOf(1, ((size.width + 3.dp.toPx()) / (3.dp.toPx() + 2.dp.toPx())).toInt())
        val renderedLevels = if (audioLevels.isEmpty()) {
            List(slotCount) { 0f }
        } else {
            val tail = audioLevels.takeLast(slotCount * 3)
            if (tail.size <= slotCount) {
                List(slotCount - tail.size) { 0f } + tail
            } else {
                List(slotCount) { index ->
                    val start = ((index.toDouble() / slotCount) * tail.size).toInt()
                    val end = maxOf(start + 1, (((index + 1).toDouble() / slotCount) * tail.size).toInt())
                    tail.subList(start, minOf(end, tail.size)).maxOrNull() ?: 0f
                }
            }
        }

        val spacing = 2.dp.toPx()
        val totalSpacing = spacing * (renderedLevels.size - 1).coerceAtLeast(0)
        val barWidth = maxOf(1.dp.toPx(), (size.width - totalSpacing) / renderedLevels.size)
        val midY = size.height / 2f

        renderedLevels.forEachIndexed { index, level ->
            val height = 4.dp.toPx() + (size.height - 4.dp.toPx()) * level.coerceIn(0f, 1f)
            val left = index * (barWidth + spacing)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.12f + (0.48f * level.coerceIn(0f, 1f))),
                topLeft = androidx.compose.ui.geometry.Offset(left, midY - (height / 2f)),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

private fun formatVoiceDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
