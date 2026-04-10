package com.remodex.android.ui.turn

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun composerChromeContainerColor(): Color =
    MaterialTheme.colorScheme.surface.copy(
        alpha = if (isSystemInDarkTheme()) 0.98f else 0.985f
    )

@Composable
internal fun composerChromeBorderColor(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (isSystemInDarkTheme()) 0.34f else 0.14f
    )

@Composable
internal fun composerChromeSecondaryContentColor(): Color =
    if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    }

@Composable
internal fun composerChromeShadowElevation(): Dp =
    if (isSystemInDarkTheme()) 10.dp else 6.dp
