package com.remodex.android.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.ui.main.ContentViewModel

@Composable
fun SidebarScreen(
    viewModel: ContentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val threads by viewModel.threads.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val runningThreadIDs by viewModel.runningThreadIDs.collectAsState()
    val readyThreadIDs by viewModel.readyThreadIDs.collectAsState()
    val failedThreadIDs by viewModel.failedThreadIDs.collectAsState()

    val filteredThreads = remember(threads, searchQuery) {
        if (searchQuery.isBlank()) threads
        else threads.filter { t ->
            t.displayTitle.contains(searchQuery, ignoreCase = true) ||
                    (t.preview?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        SidebarHeader()

        // Search
        SidebarSearchField(
            query = searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) }
        )

        // New Chat button
        SidebarNewChatButton(
            onClick = { viewModel.startNewThread() }
        )

        // Thread list
        SidebarThreadList(
            threads = filteredThreads,
            activeThreadId = activeThreadId,
            runningThreadIDs = runningThreadIDs,
            readyThreadIDs = readyThreadIDs,
            failedThreadIDs = failedThreadIDs,
            onSelectThread = { viewModel.selectThread(it) },
            modifier = Modifier.weight(1f)
        )

        // Bottom floating buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    viewModel.closeSidebar()
                    onNavigateToSettings()
                },
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SidebarHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "R",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "Remodex",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SidebarSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("Search conversations", style = MaterialTheme.typography.bodyMedium) },
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
fun SidebarNewChatButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("New Chat")
    }
}
