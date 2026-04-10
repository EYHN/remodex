package com.remodex.android.ui.turn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remodex.android.data.model.ContextWindowUsage

private val ColorGreen = Color(0xFF34C759)
private val ColorYellow = Color(0xFFFFCC00)
private val ColorOrange = Color(0xFFFF9500)
private val ColorRed = Color(0xFFFF3B30)
private val ColorTrack = Color(0xFFD1D1D6)
private val DarkTrack = Color.White.copy(alpha = 0.58f)
private val DarkCenterFill = Color.White.copy(alpha = 0.14f)
private val LightCenterFill = Color.Black.copy(alpha = 0.035f)

private fun progressColor(fraction: Float): Color = when {
    fraction < 0.60f -> ColorGreen
    fraction < 0.80f -> ColorYellow
    fraction < 0.90f -> ColorOrange
    else -> ColorRed
}

@Composable
fun ContextWindowProgressRing(
    usage: ContextWindowUsage?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.dp
) {
    val isDarkTheme = isSystemInDarkTheme()
    val fraction = usage?.fractionUsed?.coerceIn(0f, 1f)
    val percent = usage?.percentUsed?.coerceIn(0, 100)
    val trackColor = if (isDarkTheme) DarkTrack else ColorTrack
    val centerFillColor = if (isDarkTheme) DarkCenterFill else LightCenterFill
    val arcColor = if (fraction != null) progressColor(fraction) else trackColor
    val labelColor = if (isDarkTheme && fraction == null) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
    } else {
        arcColor
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val padding = strokeWidth.toPx() / 2f
            val arcSize = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx())
            val topLeft = Offset(padding, padding)
            val innerRadius = (this.size.minDimension - strokeWidth.toPx() * 3.2f) / 2f

            drawCircle(
                color = centerFillColor,
                radius = innerRadius.coerceAtLeast(0f)
            )

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // Foreground arc
            if (fraction != null && fraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }

        if (percent != null) {
            Text(
                text = "$percent",
                color = labelColor,
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
