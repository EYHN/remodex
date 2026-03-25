package com.remodex.android.ui.about

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remodex.android.ui.theme.AccentBlue
import com.remodex.android.ui.theme.AccentGreen
import com.remodex.android.ui.theme.AccentPlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Remodex") },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("R", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Remodex", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Control Codex from your phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // How it works
            AboutSection("How It Works") {
                AboutBullet(Icons.Default.PhoneAndroid, "Your phone sends instructions through the bridge")
                AboutBullet(Icons.Default.Hub, "The bridge runs on your Mac alongside Codex")
                AboutBullet(Icons.Default.Code, "Codex executes tasks and streams responses back")
                AboutBullet(Icons.Default.Lock, "All communication is end-to-end encrypted")
            }

            // Architecture
            AboutSection("Architecture") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = """
iPhone App
  |  WebSocket (E2EE)
  v
Remodex Bridge (Mac)
  |  JSON-RPC (stdin/stdout)
  v
Codex App Server
                        """.trimIndent(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Security
            AboutSection("Pairing & Security") {
                AboutBullet(Icons.Default.QrCode2, "One-time QR code scan for initial pairing")
                AboutBullet(Icons.Default.Key, "Curve25519 ECDH key agreement")
                AboutBullet(Icons.Default.Shield, "AES-256-GCM envelope encryption")
                AboutBullet(Icons.Default.Fingerprint, "Ed25519 identity signatures")
                AboutBullet(Icons.Default.Refresh, "Automatic trusted reconnect after pairing")
            }

            // Encryption specs
            AboutSection("Encryption Details") {
                val specs = listOf(
                    "Key Agreement" to "X25519 ECDH",
                    "Signing" to "Ed25519",
                    "Envelope" to "AES-256-GCM",
                    "Key Derivation" to "HKDF-SHA256",
                    "Transcript" to "Length-prefixed concatenation"
                )
                specs.forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(120.dp))
                        Text(value, style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace))
                    }
                }
            }

            // Git commands
            AboutSection("Git Commands") {
                val commands = listOf(
                    "Commit" to "Stage and commit changes",
                    "Push" to "Push to remote",
                    "Pull" to "Pull latest changes",
                    "Branch" to "Switch or create branches",
                    "Diff" to "View working directory changes",
                    "Status" to "Repository status overview"
                )
                commands.forEach { (cmd, desc) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(cmd, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(80.dp))
                        Text(desc, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Open Source", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ISC License", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun AboutBullet(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
