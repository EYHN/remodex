package com.remodex.android.ui.sidebar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexThread

@Composable
fun SidebarThreadList(
    threads: List<CodexThread>,
    activeThreadId: String?,
    runningThreadIDs: Set<String>,
    readyThreadIDs: Set<String>,
    failedThreadIDs: Set<String>,
    onSelectThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (threads.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Group by project
    val grouped = remember(threads) {
        val projectGroups = mutableMapOf<String?, MutableList<CodexThread>>()
        for (thread in threads) {
            val key = thread.projectKey
            projectGroups.getOrPut(key) { mutableListOf() }.add(thread)
        }
        projectGroups
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        grouped.forEach { (projectKey, projectThreads) ->
            if (projectKey != null) {
                item(key = "header_$projectKey") {
                    Text(
                        text = projectKey.substringAfterLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            items(
                items = projectThreads,
                key = { it.id }
            ) { thread ->
                SidebarThreadRow(
                    thread = thread,
                    isSelected = thread.id == activeThreadId,
                    isRunning = thread.id in runningThreadIDs,
                    isReady = thread.id in readyThreadIDs,
                    isFailed = thread.id in failedThreadIDs,
                    onClick = { onSelectThread(thread.id) }
                )
            }
        }
    }
}
