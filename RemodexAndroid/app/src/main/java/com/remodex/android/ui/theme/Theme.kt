package com.remodex.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = RemodexBlack,
    onPrimary = RemodexWhite,
    primaryContainer = RemodexGray100,
    onPrimaryContainer = RemodexGray900,
    secondary = RemodexGray600,
    onSecondary = RemodexWhite,
    secondaryContainer = RemodexGray100,
    onSecondaryContainer = RemodexGray800,
    tertiary = AccentPlan,
    onTertiary = RemodexWhite,
    background = RemodexWhite,
    onBackground = RemodexBlack,
    surface = RemodexWhite,
    onSurface = RemodexBlack,
    surfaceVariant = RemodexGray50,
    onSurfaceVariant = RemodexGray600,
    outline = RemodexGray300,
    outlineVariant = RemodexGray200,
    error = StatusRed,
    onError = RemodexWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = RemodexWhite,
    onPrimary = RemodexBlack,
    primaryContainer = RemodexGray800,
    onPrimaryContainer = RemodexGray100,
    secondary = RemodexGray400,
    onSecondary = RemodexBlack,
    secondaryContainer = RemodexGray800,
    onSecondaryContainer = RemodexGray200,
    tertiary = AccentPlan,
    onTertiary = RemodexWhite,
    background = DarkBackground,
    onBackground = RemodexWhite,
    surface = DarkSurface,
    onSurface = RemodexWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = RemodexGray400,
    outline = RemodexGray600,
    outlineVariant = RemodexGray700,
    error = StatusRed,
    onError = RemodexWhite
)

@Composable
fun RemodexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RemodexTypography,
        content = content
    )
}
