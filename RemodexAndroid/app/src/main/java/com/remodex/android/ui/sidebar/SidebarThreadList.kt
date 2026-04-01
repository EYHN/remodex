package com.remodex.android.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexThread
import com.remodex.android.data.model.CodexThreadSyncState

@Composable
fun SidebarThreadList(
    threads: List<CodexThread>,
    activeThreadId: String?,
    runningThreadIDs: Set<String>,
    readyThreadIDs: Set<String>,
    failedThreadIDs: Set<String>,
    canCreateThread: Boolean,
    isInitialLoading: Boolean = false,
    onSelectThread: (String) -> Unit,
    onCreateThreadInProject: (String?) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (threads.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isInitialLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Loading chats...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "No conversations yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var expandedSubagentParentIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    val grouped = remember(threads) {
        threads
            .filter { it.syncState != CodexThreadSyncState.ARCHIVED_LOCAL }
            .groupBy { it.projectKey }
            .map { (projectKey, projectThreads) ->
                SidebarThreadGroup(
                    projectKey = projectKey,
                    threads = projectThreads.sortedByDescending {
                        it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt
                    }
                )
            }
            .sortedWith(
                compareByDescending<SidebarThreadGroup> { it.latestTimestamp }
                    .thenBy { it.projectKey ?: "" }
            )
    }

    LaunchedEffect(activeThreadId, threads) {
        val normalizedActiveThreadId = activeThreadId ?: return@LaunchedEffect
        val threadsById = threads.associateBy { it.id }
        val ancestorIds = mutableSetOf<String>()
        var parentId = threadsById[normalizedActiveThreadId]?.parentThreadId
        while (parentId != null) {
            ancestorIds += parentId
            parentId = threadsById[parentId]?.parentThreadId
        }
        if (ancestorIds.isNotEmpty()) {
            expandedSubagentParentIds = expandedSubagentParentIds + ancestorIds
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { group ->
            val hierarchy = childrenByParentId(group.threads)
            if (group.projectKey != null || grouped.size > 1) {
                item(key = "header_${group.headerKey}") {
                    SidebarProjectHeader(
                        title = group.title,
                        enabled = canCreateThread,
                        onCreateThread = { onCreateThreadInProject(group.projectKey) }
                    )
                }
            }
            items(
                items = rootThreads(group.threads),
                key = { it.id }
            ) { thread ->
                SidebarThreadTree(
                    thread = thread,
                    activeThreadId = activeThreadId,
                    runningThreadIDs = runningThreadIDs,
                    readyThreadIDs = readyThreadIDs,
                    failedThreadIDs = failedThreadIDs,
                    childrenByParentId = hierarchy,
                    expandedParentIds = expandedSubagentParentIds,
                    onToggleParent = { parentId ->
                        expandedSubagentParentIds = expandedSubagentParentIds.toMutableSet().also { expanded ->
                            if (!expanded.add(parentId)) {
                                expanded.remove(parentId)
                            }
                        }
                    },
                    onSelectThread = onSelectThread,
                    onRenameThread = onRenameThread,
                    onArchiveThread = onArchiveThread,
                    onDeleteThread = onDeleteThread
                )
            }
        }
    }
}

@Composable
private fun SidebarProjectHeader(
    title: String,
    enabled: Boolean,
    onCreateThread: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.weight(1f))

        FilledTonalIconButton(
            onClick = onCreateThread,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(999.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "New thread in project",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private data class SidebarThreadGroup(
    val projectKey: String?,
    val threads: List<CodexThread>
) {
    val headerKey: String = projectKey ?: "general"
    val title: String = projectKey?.substringAfterLast('/') ?: "General"
    val latestTimestamp: Long = threads.maxOfOrNull {
        it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt
    } ?: 0L
}

private fun rootThreads(threads: List<CodexThread>): List<CodexThread> {
    val ids = threads.map { it.id }.toSet()
    return threads.filter { thread ->
        val parentId = thread.parentThreadId
        parentId == null || parentId !in ids
    }
}

private fun childrenByParentId(threads: List<CodexThread>): Map<String, List<CodexThread>> =
    threads
        .filter { !it.parentThreadId.isNullOrBlank() }
        .groupBy { requireNotNull(it.parentThreadId) }
        .mapValues { (_, children) ->
            children.sortedByDescending { it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt }
        }

@Composable
private fun SidebarThreadTree(
    thread: CodexThread,
    activeThreadId: String?,
    runningThreadIDs: Set<String>,
    readyThreadIDs: Set<String>,
    failedThreadIDs: Set<String>,
    childrenByParentId: Map<String, List<CodexThread>>,
    expandedParentIds: Set<String>,
    onToggleParent: (String) -> Unit,
    onSelectThread: (String) -> Unit,
    onRenameThread: (String, String) -> Unit,
    onArchiveThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    depth: Int = 0
) {
    val children = childrenByParentId[thread.id].orEmpty()
    val isExpanded = thread.id in expandedParentIds
    SidebarThreadRow(
        thread = thread,
        isSelected = thread.id == activeThreadId,
        isRunning = thread.id in runningThreadIDs,
        isReady = thread.id in readyThreadIDs,
        isFailed = thread.id in failedThreadIDs,
        indentLevel = depth,
        hasChildren = children.isNotEmpty(),
        isExpanded = isExpanded,
        onToggleChildren = if (children.isNotEmpty()) {
            { onToggleParent(thread.id) }
        } else {
            null
        },
        onClick = { onSelectThread(thread.id) },
        onRenameThread = { onRenameThread(thread.id, it) },
        onArchiveThread = { onArchiveThread(thread.id) },
        onDeleteThread = { onDeleteThread(thread.id) }
    )

    if (children.isNotEmpty() && isExpanded) {
        children.forEach { child ->
            SidebarThreadTree(
                thread = child,
                activeThreadId = activeThreadId,
                runningThreadIDs = runningThreadIDs,
                readyThreadIDs = readyThreadIDs,
                failedThreadIDs = failedThreadIDs,
                childrenByParentId = childrenByParentId,
                expandedParentIds = expandedParentIds,
                onToggleParent = onToggleParent,
                onSelectThread = onSelectThread,
                onRenameThread = onRenameThread,
                onArchiveThread = onArchiveThread,
                onDeleteThread = onDeleteThread,
                depth = depth + 1
            )
        }
    }
}
