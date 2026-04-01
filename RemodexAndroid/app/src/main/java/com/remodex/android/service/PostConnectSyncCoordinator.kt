package com.remodex.android.service

class PostConnectSyncCoordinator(
    private val refreshAccountStatus: suspend () -> Unit,
    private val refreshModels: suspend () -> Unit,
    private val syncThreadList: suspend () -> Unit,
    private val refreshTrackedRunningTurnStates: suspend () -> Unit,
    private val routePendingNotificationOpenIfPossible: suspend (Boolean) -> Boolean,
    private val activeThreadId: () -> String?,
    private val hydrateThreadHistory: suspend (String) -> Unit,
    private val onBootstrapStateChanged: (Boolean) -> Unit,
    private val onBootstrapCompleted: () -> Unit
) {
    suspend fun run() {
        try {
            refreshAccountStatus()
            refreshModels()
            syncThreadList()
            refreshTrackedRunningTurnStates()
            routePendingNotificationOpenIfPossible(false)
            activeThreadId()?.let { threadId ->
                hydrateThreadHistory(threadId)
            }
            onBootstrapCompleted()
        } finally {
            onBootstrapStateChanged(false)
        }
    }
}
