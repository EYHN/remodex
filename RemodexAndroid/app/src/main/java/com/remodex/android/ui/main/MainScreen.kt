package com.remodex.android.ui.main

import android.Manifest
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import com.remodex.android.data.model.CodexThread
import com.remodex.android.service.CodexConnectionRecoveryState
import com.remodex.android.ui.home.HomeEmptyStateScreen
import com.remodex.android.ui.sidebar.NewChatProjectPickerSheet
import com.remodex.android.ui.sidebar.SidebarScreen
import com.remodex.android.ui.sidebar.SidebarThreadGrouping
import com.remodex.android.ui.turn.TurnScreen

@Composable
fun MainScreen(
    viewModel: ContentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val isSidebarOpen by viewModel.isSidebarOpen.collectAsState()
    val threads by viewModel.threads.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()
    val connectionRecoveryState by viewModel.connectionRecoveryState.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    val shouldPromptForNotificationPermission by viewModel.shouldPromptForNotificationPermission.collectAsState()
    val missingNotificationThreadPrompt by viewModel.missingNotificationThreadPrompt.collectAsState()
    val newChatProjectChoices = remember(threads) {
        SidebarThreadGrouping.makeProjectChoices(threads)
    }
    val connectionStatusMessage = remember(connectionRecoveryState) {
        when (val recoveryState = connectionRecoveryState) {
            CodexConnectionRecoveryState.Idle -> null
            is CodexConnectionRecoveryState.Retrying -> recoveryState.message
        }
    }
    var isShowingNewChatProjectPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshNotificationPermissionState()
    }

    val sidebarWidth = 330.dp

    val sidebarOffset by animateDpAsState(
        targetValue = if (isSidebarOpen) 0.dp else (-sidebarWidth),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "sidebar"
    )

    fun startNewChatInProject(projectPath: String? = null) {
        if (connectionPhase != com.remodex.android.service.CodexConnectionPhase.CONNECTED) {
            return
        }
        val normalizedProjectPath = CodexThread.normalizeProjectPath(projectPath)
        viewModel.startNewThread(normalizedProjectPath)
    }

    fun requestNewChat() {
        if (connectionPhase != com.remodex.android.service.CodexConnectionPhase.CONNECTED) {
            return
        }

        if (newChatProjectChoices.isEmpty()) {
            viewModel.startNewThread()
            return
        }

        isShowingNewChatProjectPicker = true
    }

    LaunchedEffect(isConnected) {
        viewModel.refreshNotificationPermissionState()
    }

    LaunchedEffect(shouldPromptForNotificationPermission) {
        if (!shouldPromptForNotificationPermission) {
            return@LaunchedEffect
        }

        viewModel.markNotificationPermissionPrompted()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 15 && !isSidebarOpen) {
                            viewModel.openSidebar()
                        } else if (dragAmount < -15 && isSidebarOpen) {
                            viewModel.closeSidebar()
                        }
                    }
                )
            }
    ) {
        // Main content
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (activeThreadId != null) {
                TurnScreen(
                    viewModel = viewModel,
                    onOpenSidebar = { viewModel.openSidebar() },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToScanner = onNavigateToScanner,
                    onStartNewChatInProject = ::startNewChatInProject
                )
            } else {
                HomeEmptyStateScreen(
                    threadCount = threads.size,
                    connectionPhase = connectionPhase,
                    connectionErrorMessage = connectionErrorMessage,
                    connectionStatusMessage = connectionStatusMessage,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() },
                    onScanQR = onNavigateToScanner,
                    onOpenSidebar = { viewModel.openSidebar() },
                    onStartNewChat = ::requestNewChat
                )
            }
        }

        missingNotificationThreadPrompt?.let {
            AlertDialog(
                onDismissRequest = { viewModel.dismissMissingNotificationThreadPrompt() },
                title = { Text("Conversation not ready") },
                text = {
                    Text(
                        "The thread from this notification is not available yet. " +
                            "It will open automatically if it appears after the next sync."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.retryPendingNotificationRoute()
                            }
                        }
                    ) {
                        Text("Retry now")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissMissingNotificationThreadPrompt() }
                    ) {
                        Text("Dismiss")
                    }
                }
            )
        }

        // Dim overlay when sidebar open
        if (isSidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.closeSidebar() }
            )
        }

        // Sidebar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(sidebarWidth)
                .offset { IntOffset(sidebarOffset.roundToPx(), 0) }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            SidebarScreen(
                viewModel = viewModel,
                onNavigateToSettings = onNavigateToSettings,
                onRequestNewChat = ::requestNewChat,
                onStartNewChatInProject = ::startNewChatInProject
            )
        }

        if (isShowingNewChatProjectPicker) {
            NewChatProjectPickerSheet(
                choices = newChatProjectChoices,
                onDismiss = { isShowingNewChatProjectPicker = false },
                onSelectProject = { projectPath ->
                    isShowingNewChatProjectPicker = false
                    startNewChatInProject(projectPath)
                },
                onSelectWithoutProject = {
                    isShowingNewChatProjectPicker = false
                    startNewChatInProject(null)
                }
            )
        }
    }
}
