package com.remodex.android.ui.sidebar

import com.remodex.android.data.model.CodexThread
import com.remodex.android.data.model.CodexThreadSyncState

data class SidebarProjectChoice(
    val id: String,
    val label: String,
    val projectPath: String,
    val fullPath: String,
    val sortTimestamp: Long
)

object SidebarThreadGrouping {
    fun makeProjectChoices(threads: List<CodexThread>): List<SidebarProjectChoice> {
        return threads
            .filter { thread ->
                thread.syncState != CodexThreadSyncState.ARCHIVED_LOCAL
                    && !thread.projectKey.isNullOrBlank()
            }
            .groupBy { thread -> requireNotNull(thread.projectKey) }
            .mapNotNull { (projectPath, projectThreads) ->
                val representativeThread = projectThreads.maxWithOrNull(
                    compareBy<CodexThread> { thread ->
                        thread.updatedAt.takeIf { updatedAt -> updatedAt > 0L } ?: thread.createdAt
                    }.thenBy { thread -> thread.id }
                ) ?: return@mapNotNull null

                SidebarProjectChoice(
                    id = "project:$projectPath",
                    label = representativeThread.projectDisplayName ?: projectPath.substringAfterLast('/'),
                    projectPath = projectPath,
                    fullPath = projectPath,
                    sortTimestamp = representativeThread.updatedAt.takeIf { it > 0L } ?: representativeThread.createdAt
                )
            }
            .sortedWith(
                compareByDescending<SidebarProjectChoice> { it.sortTimestamp }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.id }
            )
    }
}
