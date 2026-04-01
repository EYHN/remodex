package com.remodex.android.ui.shared

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Applies a frosted-glass / glass-morphism effect.
 *
 * - On Android 12+ (API 31+): uses [RenderEffect.createBlurEffect] for a real
 *   background blur, layered behind a semi-transparent tinted surface.
 * - On older versions: falls back to a semi-transparent surface color that
 *   approximates the tinted-glass look without blur.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.adaptiveGlass())
 * Box(modifier = Modifier.adaptiveGlass(shape = CircleShape))
 * ```
 */
fun Modifier.adaptiveGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    blurRadius: Float = 24f,
): Modifier = composed {
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .clip(shape)
            .graphicsLayer {
                renderEffect = RenderEffect
                    .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
            .background(color = tint, shape = shape)
    } else {
        this
            .clip(shape)
            .background(color = tint, shape = shape)
    }
}

/**
 * Variant that uses the default rounded-rectangle shape and accepts only a
 * blur radius override, for the most common call-site pattern.
 */
fun Modifier.adaptiveGlass(blurRadius: Float): Modifier =
    adaptiveGlass(shape = RoundedCornerShape(16.dp), blurRadius = blurRadius)
