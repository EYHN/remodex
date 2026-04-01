// FILE: HapticFeedback.kt
// Purpose: Centralized haptic feedback utility for premium button interactions.
// Layer: Service
// Exports: HapticFeedback
// Depends on: Android Vibrator API

package com.remodex.android.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Light/medium/heavy haptic impact, matching iOS UIImpactFeedbackGenerator styles. */
    fun impact(style: ImpactStyle = ImpactStyle.MEDIUM) {
        val vibrator = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (duration, amplitude) = when (style) {
                ImpactStyle.LIGHT  -> 20L to 40
                ImpactStyle.MEDIUM -> 30L to 120
                ImpactStyle.HEAVY  -> 50L to 200
            }
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, amplitude)
            )
        } else {
            @Suppress("DEPRECATION")
            val duration = when (style) {
                ImpactStyle.LIGHT  -> 20L
                ImpactStyle.MEDIUM -> 30L
                ImpactStyle.HEAVY  -> 50L
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    /** Success/warning/error notification feedback, matching iOS UINotificationFeedbackGenerator. */
    fun notification(type: NotificationType = NotificationType.SUCCESS) {
        val vibrator = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                NotificationType.SUCCESS -> {
                    // Double tap pattern: short-pause-short
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 80, 30),
                        intArrayOf(0, 120, 0, 80),
                        -1
                    )
                }
                NotificationType.WARNING -> {
                    // Two even pulses
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 40, 60, 40),
                        intArrayOf(0, 160, 0, 160),
                        -1
                    )
                }
                NotificationType.ERROR -> {
                    // Three sharp pulses
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 40, 50, 40, 50, 40),
                        intArrayOf(0, 200, 0, 200, 0, 200),
                        -1
                    )
                }
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val pattern = when (type) {
                NotificationType.SUCCESS -> longArrayOf(0, 30, 80, 30)
                NotificationType.WARNING -> longArrayOf(0, 40, 60, 40)
                NotificationType.ERROR   -> longArrayOf(0, 40, 50, 40, 50, 40)
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /** Subtle tick for selection changes, matching iOS UISelectionFeedbackGenerator. */
    fun selectionChanged() {
        val vibrator = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(10, 40))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }
    enum class NotificationType { SUCCESS, WARNING, ERROR }
}
