package com.remodex.android.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThreadHistoryHydrator(
    private val scope: CoroutineScope,
    private val isThreadArchived: (String) -> Boolean,
    private val syncThreadHistory: suspend (String) -> Unit,
    private val shouldTreatErrorAsHydrated: (Exception) -> Boolean,
    private val logTag: String
) {
    private val hydratedThreadIds = mutableSetOf<String>()
    private val _loadingThreadIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingThreadIds: StateFlow<Set<String>> = _loadingThreadIds.asStateFlow()

    fun reset() {
        hydratedThreadIds.clear()
        _loadingThreadIds.value = emptySet()
    }

    fun isLoading(threadId: String?): Boolean {
        val normalizedThreadId = threadId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return normalizedThreadId in _loadingThreadIds.value
    }

    fun markHydrated(threadId: String) {
        hydratedThreadIds += threadId
    }

    fun launchLoad(
        threadId: String,
        forceRefresh: Boolean = false,
        markHydratedWhenNotMaterialized: Boolean = true
    ) {
        scope.launch {
            loadIfNeeded(
                threadId = threadId,
                forceRefresh = forceRefresh,
                markHydratedWhenNotMaterialized = markHydratedWhenNotMaterialized
            )
        }
    }

    suspend fun loadIfNeeded(
        threadId: String,
        forceRefresh: Boolean = false,
        markHydratedWhenNotMaterialized: Boolean = true
    ) {
        if (isThreadArchived(threadId)) {
            return
        }

        if (!forceRefresh && hydratedThreadIds.contains(threadId)) {
            return
        }
        if (threadId in _loadingThreadIds.value) {
            return
        }

        _loadingThreadIds.value = _loadingThreadIds.value + threadId
        try {
            syncThreadHistory(threadId)
            hydratedThreadIds += threadId
        } catch (error: Exception) {
            if (markHydratedWhenNotMaterialized && shouldTreatErrorAsHydrated(error)) {
                hydratedThreadIds += threadId
                return
            }
            Log.e(logTag, "Load history failed: ${error.message}")
        } finally {
            _loadingThreadIds.value = _loadingThreadIds.value - threadId
        }
    }
}
