package com.remodex.android.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.android.R
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.ui.theme.*

@Composable
fun HomeEmptyStateScreen(
    threadCount: Int,
    connectionPhase: CodexConnectionPhase,
    connectionErrorMessage: String?,
    connectionStatusMessage: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScanQR: () -> Unit,
    onOpenSidebar: () -> Unit,
    onStartNewChat: () -> Unit
) {
    val isConnected = connectionPhase == CodexConnectionPhase.CONNECTED
    val isConnecting = connectionPhase in listOf(
        CodexConnectionPhase.CONNECTING,
        CodexConnectionPhase.HANDSHAKING,
        CodexConnectionPhase.LOADING_CHATS,
        CodexConnectionPhase.SYNCING
    )
    val hasThreads = threadCount > 0

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
        Text(
            "Remodex",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
                .align(Alignment.TopStart),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = onOpenSidebar) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 14.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.remodex_app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    ),
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

            Spacer(modifier = Modifier.height(16.dp))

            connectionStatusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            when {
                isConnected -> {
                    Text(
                        text = if (hasThreads) {
                            "Start a new chat or open the menu to switch conversations."
                        } else {
                            "Connected to your Mac. Start your first chat."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onStartNewChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text(if (hasThreads) "New Chat" else "Start First Chat")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onOpenSidebar) {
                        Text(if (hasThreads) "Browse Conversations" else "Open Menu")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                else -> {
                    val offlineMessage = connectionErrorMessage ?: "Not paired"
                    Text(
                        text = offlineMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = if (isConnecting) onDisconnect else onScanQR,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.surface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isConnecting) "Cancel" else "Scan QR Code",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
