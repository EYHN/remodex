package com.remodex.android

import android.content.pm.ApplicationInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.remodex.android.ui.debug.TurnExploreScreen
import com.remodex.android.service.NotificationIntentRouter
import com.remodex.android.service.CodexService
import com.remodex.android.ui.navigation.RemodexNavGraph
import com.remodex.android.ui.theme.RemodexTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_DEBUG_SCREEN = "debug_screen"
        private const val DEBUG_SCREEN_TURN_EXPLORE = "turn_explore"
    }

    @Inject lateinit var codexService: CodexService
    @Inject lateinit var notificationIntentRouter: NotificationIntentRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationIntentRouter.handleLaunchIntent(intent)
        enableEdgeToEdge()
        val debugScreen = resolveDebugScreen(intent)
        setContent {
            RemodexTheme {
                when (debugScreen) {
                    DEBUG_SCREEN_TURN_EXPLORE -> TurnExploreScreen()
                    else -> RemodexNavGraph()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        codexService.setAppInForeground(true)
        codexService.refreshNotificationPermissionState()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        codexService.setAppInForeground(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        val previousDebugScreen = resolveDebugScreen(getIntent())
        super.onNewIntent(intent)
        setIntent(intent)
        notificationIntentRouter.handleLaunchIntent(intent)
        if (resolveDebugScreen(intent) != previousDebugScreen) {
            recreate()
        }
    }

    private fun resolveDebugScreen(intent: Intent?): String? {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return null
        }
        return intent?.getStringExtra(EXTRA_DEBUG_SCREEN)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
