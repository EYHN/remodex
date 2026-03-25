package com.remodex.android.ui.main

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.ui.home.HomeEmptyStateScreen
import com.remodex.android.ui.sidebar.SidebarScreen
import com.remodex.android.ui.turn.TurnScreen

@Composable
fun MainScreen(
    viewModel: ContentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val isSidebarOpen by viewModel.isSidebarOpen.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()

    val sidebarWidth = 330.dp
    val sidebarWidthPx = with(LocalDensity.current) { sidebarWidth.toPx() }

    val sidebarOffset by animateDpAsState(
        targetValue = if (isSidebarOpen) 0.dp else (-sidebarWidth),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "sidebar"
    )

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
                    onOpenSidebar = { viewModel.openSidebar() }
                )
            } else {
                HomeEmptyStateScreen(
                    connectionPhase = connectionPhase,
                    onConnect = { viewModel.connect() },
                    onDisconnect = { viewModel.disconnect() },
                    onScanQR = onNavigateToScanner,
                    onOpenSidebar = { viewModel.openSidebar() }
                )
            }
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
                onNavigateToScanner = onNavigateToScanner
            )
        }
    }
}
