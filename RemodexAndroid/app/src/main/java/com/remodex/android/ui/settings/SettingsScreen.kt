package com.remodex.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.ui.main.ContentViewModel
import com.remodex.android.ui.theme.StatusGreen
import com.remodex.android.ui.theme.StatusRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ContentViewModel,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val selectedAccessMode by viewModel.selectedAccessMode.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val bridgeVersionInfo by viewModel.bridgeVersionInfo.collectAsState()
    val rateLimitBuckets by viewModel.rateLimitBuckets.collectAsState()
    val contextWindowUsage by viewModel.contextWindowUsage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status
            SettingsCard(title = "Connection") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Status",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (isConnected) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) StatusGreen else StatusRed
                    )
                }
                bridgeVersionInfo?.let { version ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bridge Version", style = MaterialTheme.typography.bodyMedium)
                        Text(version, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan New QR Code")
                }
                if (viewModel.hasSavedRelay()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.forgetPairing() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Forget Pairing", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Access mode
            SettingsCard(title = "Access Control") {
                CodexAccessMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAccessMode == mode,
                            onClick = { viewModel.setAccessMode(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(mode.displayLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (mode) {
                                    CodexAccessMode.ON_REQUEST -> "Ask before each action"
                                    CodexAccessMode.FULL_ACCESS -> "Auto-approve all actions"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Model selection
            if (availableModels.isNotEmpty()) {
                SettingsCard(title = "Model") {
                    availableModels.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModel == model.id || (selectedModel == null && model.isDefault),
                                onClick = { viewModel.setSelectedModel(model.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(model.label, style = MaterialTheme.typography.bodyMedium)
                                model.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Usage
            SettingsCard(title = "Usage") {
                contextWindowUsage?.let { usage ->
                    Text("Context Window", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { usage.fractionUsed },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${usage.percentUsed}% used (${usage.tokensUsed} / ${usage.tokenLimit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(onClick = { viewModel.refreshRateLimits() }) {
                    Text("Refresh Usage")
                }
            }

            // About
            SettingsCard(title = "About") {
                TextButton(
                    onClick = onNavigateToAbout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("About Remodex")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
