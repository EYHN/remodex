package com.remodex.android.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.data.model.CodexGPTAccountSnapshot
import com.remodex.android.data.model.CodexGPTAccountStatus
import com.remodex.android.data.model.CodexRateLimitDisplayRow
import com.remodex.android.data.model.CodexServiceTier
import com.remodex.android.data.model.CodexThread
import com.remodex.android.data.model.CodexThreadSyncState
import com.remodex.android.service.CodexConnectionPhase
import com.remodex.android.service.CodexConnectionRecoveryState
import com.remodex.android.ui.main.ContentViewModel
import com.remodex.android.ui.theme.StatusGreen
import com.remodex.android.ui.theme.StatusRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ContentViewModel,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToArchivedChats: () -> Unit
) {
    val context = LocalContext.current
    val threads by viewModel.threads.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val selectedAccessMode by viewModel.selectedAccessMode.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val selectedReasoningEffort by viewModel.selectedReasoningEffort.collectAsState()
    val selectedServiceTier by viewModel.selectedServiceTier.collectAsState()
    val bridgeVersionInfo by viewModel.bridgeVersionInfo.collectAsState()
    val contextWindowUsage by viewModel.contextWindowUsage.collectAsState()
    val rateLimitBuckets by viewModel.rateLimitBuckets.collectAsState()
    val gptAccountSnapshot by viewModel.gptAccountSnapshot.collectAsState()
    val gptAccountErrorMessage by viewModel.gptAccountErrorMessage.collectAsState()
    val connectionPhase by viewModel.connectionPhase.collectAsState()
    val connectionRecoveryState by viewModel.connectionRecoveryState.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    val trustedPairPresentation by viewModel.trustedPairPresentation.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationPermissionGranted by viewModel.notificationPermissionGranted.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isOpeningLogin by remember { mutableStateOf(false) }
    var isCancellingLogin by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var isMacNameDialogOpen by remember { mutableStateOf(false) }
    var pendingMacNickname by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshNotificationPermissionState()
    }

    val archivedThreads = remember(threads) {
        threads
            .filter { it.syncState == CodexThreadSyncState.ARCHIVED_LOCAL }
            .sortedByDescending {
                it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt
            }
    }

    val selectedModelOption = remember(availableModels, selectedModel) {
        availableModels.firstOrNull { model -> model.id == selectedModel || model.model == selectedModel }
    }
    val selectedReasoningLabel = remember(selectedReasoningEffort) {
        selectedReasoningEffort?.replaceFirstChar { it.uppercase() } ?: "Auto"
    }
    val supportedReasoningEfforts = remember(availableModels, selectedModel) {
        viewModel.supportedReasoningEffortsForSelectedModel()
    }

    LaunchedEffect(isConnected) {
        viewModel.refreshAccountStatus()
        viewModel.refreshNotificationPermissionState()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsSection(
                    title = "Archived Chats"
                ) {
                    SettingsNavigationRow(
                        icon = Icons.Default.Archive,
                        title = "Archived Chats",
                        subtitle = if (archivedThreads.isEmpty()) {
                            "No archived chats"
                        } else {
                            "${archivedThreads.size} archived"
                        },
                        trailing = archivedThreads.size.takeIf { it > 0 }?.toString(),
                        onClick = onNavigateToArchivedChats
                    )
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    SettingsValueRow(
                        icon = Icons.Default.TextFields,
                        title = "Font",
                        value = "System",
                        subtitle = "Uses the device typography on Android"
                    )
                }
            }

            item {
                SettingsSection(title = "Notifications") {
                    SettingsStatusRow(
                        title = "Status",
                        value = if (notificationsEnabled) "Enabled" else "Disabled",
                        valueColor = if (notificationsEnabled) StatusGreen else StatusRed
                    )
                    SettingsDivider()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                        SettingsPrimaryButton(
                            label = "Request notification permission",
                            isLoading = false,
                            enabled = true
                        ) {
                            viewModel.markNotificationPermissionPrompted()
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    SettingsActionRow(
                        icon = Icons.Default.Notifications,
                        title = if (notificationsEnabled) "Manage notifications" else "Enable notifications",
                        subtitle = "Open Android app notification settings",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "ChatGPT") {
                    SettingsStatusRow(
                        title = "Status",
                        value = gptAccountSnapshot.statusLabel,
                        valueColor = gptStatusColor(gptAccountSnapshot)
                    )

                    gptAccountSnapshot.detailText?.let { detail ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    gptHintText(gptAccountSnapshot, isConnected)?.let { hint ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    gptAccountErrorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!gptAccountSnapshot.isAuthenticated) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsPrimaryButton(
                            label = gptLoginButtonTitle(gptAccountSnapshot),
                            isLoading = isOpeningLogin,
                            enabled = isConnected && !isOpeningLogin
                        ) {
                            coroutineScope.launch {
                                isOpeningLogin = true
                                val authUrl = viewModel.startOrResumeGPTLoginOnPhone()
                                isOpeningLogin = false

                                if (!authUrl.isNullOrBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    }

                    if (gptAccountSnapshot.hasActiveLogin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSecondaryButton(
                            label = "Cancel login",
                            isLoading = isCancellingLogin,
                            enabled = !isCancellingLogin
                        ) {
                            coroutineScope.launch {
                                isCancellingLogin = true
                                viewModel.cancelGPTLogin()
                                isCancellingLogin = false
                            }
                        }
                    }

                    if (gptAccountSnapshot.canLogout) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSecondaryButton(
                            label = "Log out",
                            isLoading = isLoggingOut,
                            enabled = !isLoggingOut,
                            destructive = true
                        ) {
                            coroutineScope.launch {
                                isLoggingOut = true
                                viewModel.logoutGPTAccount()
                                isLoggingOut = false
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Runtime Defaults") {
                    SettingsPickerRow(
                        icon = Icons.Default.DragIndicator,
                        title = "Model",
                        value = selectedModelOption?.label ?: "Auto",
                        options = buildList {
                            add(SettingsPickerOption(id = "__AUTO__", label = "Auto"))
                            addAll(
                                availableModels.map { model ->
                                    SettingsPickerOption(id = model.id, label = model.label)
                                }
                            )
                        },
                        onSelect = { selection ->
                            viewModel.setSelectedModel(selection.takeUnless { it == "__AUTO__" })
                        }
                    )
                    SettingsDivider()
                    SettingsPickerRow(
                        icon = Icons.Default.Psychology,
                        title = "Reasoning",
                        value = selectedReasoningLabel,
                        options = buildList {
                            add(SettingsPickerOption(id = "__AUTO__", label = "Auto"))
                            addAll(
                                supportedReasoningEfforts.map { effort ->
                                    SettingsPickerOption(
                                        id = effort,
                                        label = effort.replaceFirstChar { it.uppercase() }
                                    )
                                }
                            )
                        },
                        onSelect = { selection ->
                            viewModel.setSelectedReasoningEffort(selection.takeUnless { it == "__AUTO__" })
                        }
                    )
                    SettingsDivider()
                    SettingsPickerRow(
                        icon = Icons.Default.Bolt,
                        title = "Speed",
                        value = selectedServiceTier?.displayLabel ?: "Normal",
                        options = buildList {
                            add(SettingsPickerOption(id = "__NORMAL__", label = "Normal"))
                            addAll(
                                CodexServiceTier.entries.map { tier ->
                                    SettingsPickerOption(id = tier.name, label = tier.displayLabel)
                                }
                            )
                        },
                        onSelect = { selection ->
                            viewModel.setSelectedServiceTier(
                                selection.takeUnless { it == "__NORMAL__" }?.let {
                                    CodexServiceTier.valueOf(it)
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsPickerRow(
                        icon = Icons.Default.AdminPanelSettings,
                        title = "Access",
                        value = selectedAccessMode.displayLabel,
                        options = CodexAccessMode.entries.map { mode ->
                            SettingsPickerOption(id = mode.name, label = mode.displayLabel)
                        },
                        onSelect = { selection ->
                            CodexAccessMode.entries.firstOrNull { it.name == selection }?.let(viewModel::setAccessMode)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "Usage") {
                    if (contextWindowUsage != null) {
                        val usage = contextWindowUsage!!
                        Text(
                            text = "Context Window",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { usage.fractionUsed },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${usage.percentUsed}% used (${usage.tokensUsed} / ${usage.tokenLimit})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedButton(
                        onClick = { viewModel.refreshRateLimits() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh Usage")
                    }

                    val rateLimitRows = remember(rateLimitBuckets) {
                        rateLimitBuckets.flatMap { it.displayRows }
                    }
                    if (rateLimitRows.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        rateLimitRows.forEachIndexed { index, row ->
                            if (index > 0) {
                                SettingsDivider()
                            }
                            RateLimitRow(row = row)
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Bridge Version") {
                    SettingsValueRow(
                        icon = Icons.Default.Verified,
                        title = "Installed on Mac",
                        value = bridgeVersionInfo ?: "Unknown",
                        subtitle = "Connect to your local bridge to read the installed package version"
                    )
                }
            }

            item {
                SettingsSection(title = "Connection") {
                    trustedPairPresentation?.let { presentation ->
                        SettingsTrustedMacCard(
                            presentation = presentation,
                            connectionStatusLabel = connectionStatusLabel(connectionPhase),
                            onEditName = {
                                pendingMacNickname = presentation.nickname
                                isMacNameDialogOpen = true
                            }
                        )
                        SettingsDivider()
                    }
                    SettingsStatusRow(
                        title = "Status",
                        value = connectionStatusLabel(connectionPhase),
                        valueColor = connectionStatusColor(connectionPhase)
                    )
                    connectionProgressLabel(connectionPhase)?.let { progressLabel ->
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when (val recoveryState = connectionRecoveryState) {
                        CodexConnectionRecoveryState.Idle -> Unit
                        is CodexConnectionRecoveryState.Retrying -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = recoveryState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    connectionErrorMessage?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Default.QrCode2,
                        title = "Scan New QR Code",
                        subtitle = "Pair a different Mac bridge",
                        trailing = null,
                        onClick = onNavigateToScanner
                    )
                    if (viewModel.hasSavedRelay()) {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = Icons.Default.DeleteOutline,
                            title = "Forget Pairing",
                            subtitle = "Remove the saved local bridge",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.forgetPairing() }
                        )
                    }
                    if (isConnected) {
                        SettingsDivider()
                        SettingsActionRow(
                            icon = Icons.Default.Logout,
                            title = "Disconnect",
                            subtitle = "Stop the current local connection",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.disconnect() }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "About") {
                    // iOS-style: encryption info text card
                    Text(
                        text = "Chats are End-to-end encrypted between your phone and Mac. The relay only sees ciphertext and connection metadata after the secure handshake completes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.Info,
                        title = "How Remodex Works",
                        subtitle = null,
                        trailing = null,
                        onClick = onNavigateToAbout
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (isMacNameDialogOpen && trustedPairPresentation != null) {
        AlertDialog(
            onDismissRequest = { isMacNameDialogOpen = false },
            title = { Text("Edit Mac name") },
            text = {
                OutlinedTextField(
                    value = pendingMacNickname,
                    onValueChange = { pendingMacNickname = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Nickname") },
                    placeholder = { Text("Office Mac") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTrustedPairNickname(
                            trustedPairPresentation?.deviceId,
                            pendingMacNickname
                        )
                        isMacNameDialogOpen = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { isMacNameDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedChatsScreen(
    viewModel: ContentViewModel,
    onBack: () -> Unit,
) {
    val threads by viewModel.threads.collectAsState()
    val archivedThreads = remember(threads) {
        threads
            .filter { it.syncState == CodexThreadSyncState.ARCHIVED_LOCAL }
            .sortedByDescending {
                it.updatedAt.takeIf { updated -> updated > 0L } ?: it.createdAt
            }
    }
    var threadPendingDeletion by remember { mutableStateOf<CodexThread?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Archived Chats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (archivedThreads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No archived chats",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(archivedThreads, key = { it.id }) { thread ->
                    ArchivedThreadRow(
                        thread = thread,
                        onUnarchive = { viewModel.unarchiveThread(thread.id) },
                        onDelete = { threadPendingDeletion = thread }
                    )
                }
            }
        }
    }

    threadPendingDeletion?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadPendingDeletion = null },
            title = { Text("Delete chat") },
            text = { Text("Delete \"${thread.displayTitle}\" from the archived list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteThread(thread.id)
                        threadPendingDeletion = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { threadPendingDeletion = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        // iOS-style: subtle off-white card, no border, larger corner radius
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: () -> Unit
) {
    SettingsRowContainer(onClick = onClick) {
        SettingsLeadingIcon(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    SettingsRowContainer(onClick = onClick) {
        SettingsLeadingIcon(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsPickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    options: List<SettingsPickerOption>,
    onSelect: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    SettingsRowContainer(onClick = { isExpanded = true }) {
        SettingsLeadingIcon(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { isExpanded = true }) {
                Text(value)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelect(option.id)
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsStatusRow(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    SettingsRowContainer(onClick = null) {
        SettingsLeadingIcon(icon = Icons.Default.Circle)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    SettingsRowContainer(onClick = onClick) {
        SettingsLeadingIcon(icon = icon, tint = tint)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = tint
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsPrimaryButton(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(label)
        }
    }
}

@Composable
private fun SettingsSecondaryButton(
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = label,
                color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
            )
        }
    }
}

@Composable
private fun SettingsRowContainer(
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit
) {
    val modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SettingsLeadingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    // iOS-style: plain icon, no background surface
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun RateLimitRow(row: CodexRateLimitDisplayRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${row.window.usedPercent.toInt()}% used · ${row.window.windowDurationMins} min window",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        row.window.resetsAt?.takeIf { it.isNotBlank() }?.let { resetsAt ->
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Resets at $resetsAt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun gptStatusColor(snapshot: CodexGPTAccountSnapshot) = when (snapshot.status) {
    CodexGPTAccountStatus.AUTHENTICATED ->
        if (snapshot.needsReauth) MaterialTheme.colorScheme.tertiary else StatusGreen
    CodexGPTAccountStatus.EXPIRED -> MaterialTheme.colorScheme.error
    CodexGPTAccountStatus.LOGIN_PENDING -> MaterialTheme.colorScheme.tertiary
    CodexGPTAccountStatus.NOT_LOGGED_IN,
    CodexGPTAccountStatus.UNKNOWN,
    CodexGPTAccountStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun gptHintText(snapshot: CodexGPTAccountSnapshot, isConnected: Boolean): String? {
    return when {
        snapshot.needsReauth -> "Voice on this bridge needs a fresh ChatGPT sign-in."
        snapshot.isAuthenticated && snapshot.isVoiceTokenReady -> null
        snapshot.isAuthenticated -> "Waiting for voice sync..."
        snapshot.hasActiveLogin && isConnected -> "Complete sign-in in the browser on this device."
        snapshot.hasActiveLogin -> "Reconnect to your bridge to finish sign-in."
        !isConnected -> "Connect to your bridge first."
        else -> null
    }
}

private fun gptLoginButtonTitle(snapshot: CodexGPTAccountSnapshot): String {
    return when {
        snapshot.hasActiveLogin -> "Open On Android Again"
        snapshot.needsReauth || snapshot.status == CodexGPTAccountStatus.EXPIRED -> "Sign In Again"
        else -> "Log In on Android"
    }
}

private fun connectionStatusLabel(connectionPhase: com.remodex.android.service.CodexConnectionPhase): String {
    return when (connectionPhase) {
        com.remodex.android.service.CodexConnectionPhase.OFFLINE -> "Offline"
        com.remodex.android.service.CodexConnectionPhase.CONNECTING -> "Connecting"
        com.remodex.android.service.CodexConnectionPhase.HANDSHAKING -> "Pairing"
        com.remodex.android.service.CodexConnectionPhase.LOADING_CHATS -> "Loading Chats"
        com.remodex.android.service.CodexConnectionPhase.SYNCING -> "Syncing"
        com.remodex.android.service.CodexConnectionPhase.CONNECTED -> "Connected"
    }
}

@Composable
private fun connectionStatusColor(connectionPhase: CodexConnectionPhase) =
    when (connectionPhase) {
        CodexConnectionPhase.CONNECTED -> StatusGreen
        CodexConnectionPhase.OFFLINE -> StatusRed
        CodexConnectionPhase.CONNECTING,
        CodexConnectionPhase.HANDSHAKING,
        CodexConnectionPhase.LOADING_CHATS,
        CodexConnectionPhase.SYNCING -> MaterialTheme.colorScheme.primary
    }

private fun connectionProgressLabel(connectionPhase: CodexConnectionPhase): String? {
    return when (connectionPhase) {
        CodexConnectionPhase.CONNECTING -> "Connecting to relay..."
        CodexConnectionPhase.HANDSHAKING -> "Securing local connection..."
        CodexConnectionPhase.LOADING_CHATS -> "Loading chats..."
        CodexConnectionPhase.SYNCING -> "Syncing workspace..."
        CodexConnectionPhase.CONNECTED,
        CodexConnectionPhase.OFFLINE -> null
    }
}

@Composable
private fun SettingsDivider() {
    // iOS-style thin divider
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}

@Composable
private fun SettingsTrustedMacCard(
    presentation: com.remodex.android.data.model.CodexTrustedPairPresentation,
    connectionStatusLabel: String,
    onEditName: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LaptopMac,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Mac",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = presentation.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onEditName,
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Mac name",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatusPill(label = connectionStatusLabel)
                presentation.title.trim().takeIf { it.isNotEmpty() }?.let { title ->
                    SettingsStatusPill(label = title)
                }
            }

            presentation.systemName?.takeIf { it.isNotBlank() }?.let { systemName ->
                SettingsTrustedMacDetailRow(label = "System", value = systemName)
            }

            presentation.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                SettingsTrustedMacDetailRow(label = "Status", value = detail)
            }
        }
    }
}

@Composable
private fun SettingsStatusPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SettingsTrustedMacDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ArchivedThreadRow(
    thread: CodexThread,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsLeadingIcon(icon = Icons.Default.Archive)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = thread.preview?.takeIf { it.isNotBlank() } ?: "Archived locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = formatRelativeTime(thread.updatedAt.takeIf { it > 0L } ?: thread.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUnarchive,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Unarchive")
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private data class SettingsPickerOption(
    val id: String,
    val label: String
)

private fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - timestampMs
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHour = diffMin / 60
    val diffDay = diffHour / 24

    return when {
        diffSec < 60 -> "now"
        diffMin < 60 -> "${diffMin}m"
        diffHour < 24 -> "${diffHour}h"
        diffDay < 7 -> "${diffDay}d"
        else -> "${diffDay / 7}w"
    }
}
