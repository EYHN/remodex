package com.remodex.android.ui.turn

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.service.ApprovalRequest
import com.remodex.android.ui.main.ContentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnScreen(
    viewModel: ContentViewModel,
    onOpenSidebar: () -> Unit
) {
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isRunning by viewModel.isActiveThreadRunning.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()

    val activeThread = remember(activeThreadId, threads) {
        threads.find { it.id == activeThreadId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeThread?.displayTitle ?: "Chat",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSidebar) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: thread actions */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Approval banner
            pendingApproval?.let { approval ->
                ApprovalBanner(
                    approval = approval,
                    onApprove = { viewModel.respondToApproval(true) },
                    onReject = { viewModel.respondToApproval(false) }
                )
            }

            // Message timeline
            TurnTimeline(
                messages = messages,
                isRunning = isRunning,
                modifier = Modifier.weight(1f)
            )

            // Composer
            TurnComposer(
                isRunning = isRunning,
                onSend = { text -> viewModel.sendMessage(text) },
                onStop = { viewModel.stopTurn() }
            )
        }
    }
}

@Composable
fun ApprovalBanner(
    approval: ApprovalRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Approval Required",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${approval.toolName}: ${approval.description}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("Allow") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) { Text("Deny") }
            }
        }
    }
}
