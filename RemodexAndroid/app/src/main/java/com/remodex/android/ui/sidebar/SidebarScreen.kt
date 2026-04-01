package com.remodex.android.ui.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexThreadSyncState
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.ui.main.ContentViewModel

@Composable
fun SidebarScreen(
    viewModel: ContentViewModel,
    onNavigateToSettings: () -> Unit,
    onRequestNewChat: () -> Unit,
    onStartNewChatInProject: (String?) -> Unit
) {
    val threads by viewModel.threads.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val runningThreadIDs by viewModel.runningThreadIDs.collectAsState()
    val readyThreadIDs by viewModel.readyThreadIDs.collectAsState()
    val failedThreadIDs by viewModel.failedThreadIDs.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()
    val isLoadingThreads by viewModel.isLoadingThreads.collectAsState()
    val canCreateThread = connectionPhase == CodexConnectionPhase.CONNECTED

    val liveThreads = remember(threads) {
        threads.filter { it.syncState != CodexThreadSyncState.ARCHIVED_LOCAL }
    }
    val filteredThreads = remember(liveThreads, searchQuery) {
        if (searchQuery.isBlank()) liveThreads
        else liveThreads.filter { t ->
            t.displayTitle.contains(searchQuery, ignoreCase = true) ||
                    (t.preview?.contains(searchQuery, ignoreCase = true) == true)
        }
    }
    val isInitialThreadLoad = SidebarThreadsLoadingPresentation.shouldShowOverlay(
        isLoadingThreads = isLoadingThreads,
        threadCount = liveThreads.size
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        SidebarHeader()
        Spacer(modifier = Modifier.height(8.dp))
        SidebarSearchField(
            query = searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) }
        )
        Spacer(modifier = Modifier.height(10.dp))
        SidebarNewChatButton(
            enabled = canCreateThread,
            onClick = onRequestNewChat
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            SidebarThreadList(
                threads = filteredThreads,
                activeThreadId = activeThreadId,
                runningThreadIDs = runningThreadIDs,
                readyThreadIDs = readyThreadIDs,
                failedThreadIDs = failedThreadIDs,
                canCreateThread = canCreateThread,
                isInitialLoading = isInitialThreadLoad,
                onSelectThread = { viewModel.selectThread(it) },
                onCreateThreadInProject = onStartNewChatInProject,
                onRenameThread = { threadId, name -> viewModel.renameThread(threadId, name) },
                onArchiveThread = { threadId -> viewModel.archiveThread(threadId) },
                onDeleteThread = { threadId -> viewModel.deleteThread(threadId) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // iOS-style bottom bar: gear icon left, connection status centered
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.closeSidebar()
                    onNavigateToSettings()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (isConnected) "Connected to Mac" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SidebarHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // iOS-style: blue logo icon
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF6366F1)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "\u27A4",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "Remodex",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    // iOS-style: gray rounded rect with magnifying glass, no outline border
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(42.dp),
        placeholder = {
            Text(
                "Search conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        )
    )
}

@Composable
fun SidebarNewChatButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    // iOS-style: flat row with [+] icon, no card/border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "New Chat",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
