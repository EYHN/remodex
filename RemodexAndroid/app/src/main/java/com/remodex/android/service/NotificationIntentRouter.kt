package com.remodex.android.service

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationOpenTarget(
    val threadId: String,
    val turnId: String? = null
)

object NotificationIntentExtras {
    const val ACTION_OPEN_THREAD = "com.remodex.android.action.OPEN_NOTIFICATION_THREAD"
    const val EXTRA_THREAD_ID = "notification_thread_id"
    const val EXTRA_TURN_ID = "notification_turn_id"
    const val EXTRA_SOURCE = "notification_source"
    const val SOURCE_RUN_COMPLETION = "run_completion"

    fun openTargetFrom(intent: Intent?): NotificationOpenTarget? {
        if (intent?.action != ACTION_OPEN_THREAD) {
            return null
        }

        val threadId = intent.getStringExtra(EXTRA_THREAD_ID)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val turnId = intent.getStringExtra(EXTRA_TURN_ID)?.trim()?.takeIf { it.isNotEmpty() }
        return NotificationOpenTarget(threadId = threadId, turnId = turnId)
    }
}

@Singleton
class NotificationIntentRouter @Inject constructor() {
    private val _pendingOpenTarget = MutableStateFlow<NotificationOpenTarget?>(null)
    val pendingOpenTarget = _pendingOpenTarget.asStateFlow()

    fun handleLaunchIntent(intent: Intent?) {
        val target = NotificationIntentExtras.openTargetFrom(intent) ?: return
        _pendingOpenTarget.value = target
    }

    fun clearPendingOpenTarget() {
        _pendingOpenTarget.value = null
    }
}
