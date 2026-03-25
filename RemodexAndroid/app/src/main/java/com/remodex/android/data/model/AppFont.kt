package com.remodex.android.data.model

enum class AppFontStyle {
    SYSTEM, GEIST, GEIST_MONO, JETBRAINS_MONO;

    val displayName: String get() = when (this) {
        SYSTEM -> "System"
        GEIST -> "Geist"
        GEIST_MONO -> "Geist Mono"
        JETBRAINS_MONO -> "JetBrains Mono"
    }
}
