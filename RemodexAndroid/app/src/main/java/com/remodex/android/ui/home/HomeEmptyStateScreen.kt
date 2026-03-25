package com.remodex.android.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.ui.theme.*

@Composable
fun HomeEmptyStateScreen(
    connectionPhase: CodexConnectionPhase,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScanQR: () -> Unit,
    onOpenSidebar: () -> Unit
) {
    val isConnected = connectionPhase == CodexConnectionPhase.CONNECTED
    val isConnecting = connectionPhase in listOf(
        CodexConnectionPhase.CONNECTING,
        CodexConnectionPhase.HANDSHAKING,
        CodexConnectionPhase.LOADING_CHATS,
        CodexConnectionPhase.SYNCING
    )

    // Pulsing animation for status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusColor = when (connectionPhase) {
        CodexConnectionPhase.CONNECTED -> StatusGreen
        CodexConnectionPhase.CONNECTING, CodexConnectionPhase.HANDSHAKING -> StatusOrange
        CodexConnectionPhase.LOADING_CHATS, CodexConnectionPhase.SYNCING -> StatusBlue
        CodexConnectionPhase.OFFLINE -> RemodexGray400
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Top bar with menu button
        IconButton(
            onClick = onOpenSidebar,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo placeholder
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "R",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status dot + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            statusColor.copy(alpha = if (isConnecting) pulseAlpha else 1f)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (connectionPhase) {
                        CodexConnectionPhase.OFFLINE -> "Offline"
                        CodexConnectionPhase.CONNECTING -> "Connecting..."
                        CodexConnectionPhase.HANDSHAKING -> "Securing connection..."
                        CodexConnectionPhase.LOADING_CHATS -> "Loading chats..."
                        CodexConnectionPhase.SYNCING -> "Syncing..."
                        CodexConnectionPhase.CONNECTED -> "Connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary action button
            Button(
                onClick = when {
                    isConnected -> onDisconnect
                    isConnecting -> onDisconnect
                    else -> onConnect
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected || isConnecting)
                        MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        isConnected -> "Disconnect"
                        isConnecting -> "Cancel"
                        else -> "Connect"
                    },
                    color = if (isConnected || isConnecting)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary action
            if (!isConnected && !isConnecting) {
                TextButton(onClick = onScanQR) {
                    Text("Scan New QR Code")
                }
            }
        }
    }
}
