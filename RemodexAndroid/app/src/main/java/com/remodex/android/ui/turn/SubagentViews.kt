// FILE: SubagentViews.kt
// Purpose: UI components for multi-agent orchestration cards in the timeline.
// Layer: View Components
// Exports: SubagentActionCard, SubagentLabelParser, SubagentColorPalette
// Depends on: Jetpack Compose, Material3, CodexSubagentAction, CodexSubagentThreadPresentation

package com.remodex.android.ui.turn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexSubagentAction
import com.remodex.android.data.model.CodexSubagentThreadPresentation
import com.remodex.android.data.model.CodexThread
import com.remodex.android.ui.theme.StatusGreen
import com.remodex.android.ui.theme.StatusOrange
import com.remodex.android.ui.theme.StatusRed

// ---------------------------------------------------------------------------
// MARK: - Card (timeline-level container)
// ---------------------------------------------------------------------------

/**
 * Collapsible card showing multi-agent activity in the timeline.
 * Renders a summary header, expandable agent rows, and a typing indicator
 * when the action is still streaming.
 *
 * Accepts [threads] and [runningThreadIDs] as parameters so the composable
 * stays decoupled from [CodexService], matching existing view patterns.
 */
@Composable
fun SubagentActionCard(
    action: CodexSubagentAction,
    isStreaming: Boolean,
    threads: List<CodexThread>,
    runningThreadIDs: Set<String>,
    onOpenSubagentThread: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedAgentDetails by remember { mutableStateOf<CodexSubagentThreadPresentation?>(null) }
    val agentRows = remember(action) { action.agentRows() }

    val agentRowSpacing = when (action.normalizedTool) {
        "wait", "waitagent", "resumeagent" -> 9.dp
        "spawnagent" -> 2.dp
        else -> 4.dp
    }
    val agentRowsTopPadding = when (action.normalizedTool) {
        "wait", "waitagent", "resumeagent" -> 3.dp
        else -> 2.dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = action.summaryText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Agent rows
        AnimatedVisibility(
            visible = isExpanded && agentRows.isNotEmpty(),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = agentRowsTopPadding),
                verticalArrangement = Arrangement.spacedBy(agentRowSpacing)
            ) {
                agentRows.forEach { agent ->
                    val title = resolvedAgentTitle(agent)
                    val status = resolvedStatus(agent, action, runningThreadIDs)
                    val statusText = readableStatus(action.normalizedTool, status.label)
                    val detailModelLabel = resolvedModelLabel(agent, threads)
                    val hasDetails = detailText(agent, action) != null || detailModelLabel != null

                    SubagentAgentRow(
                        title = title,
                        status = status,
                        statusText = statusText,
                        modelLabel = if (action.normalizedTool == "spawnagent") null else detailModelLabel,
                        showsDetails = hasDetails,
                        onShowDetails = { selectedAgentDetails = agent },
                        onOpen = if (onOpenSubagentThread != null && agent.threadId != null) {
                            { onOpenSubagentThread(agent.threadId!!) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        // Streaming typing indicator
        if (isStreaming) {
            Spacer(modifier = Modifier.height(if (agentRows.isEmpty()) 2.dp else 3.dp))
            SubagentTypingIndicator()
        }
    }

    // Detail sheet
    selectedAgentDetails?.let { agent ->
        val title = resolvedAgentTitle(agent)
        val status = resolvedStatus(agent, action, runningThreadIDs)
        val statusTextForSheet = readableStatus(action.normalizedTool, status.label)
        val modelLabel = resolvedModelLabel(agent, threads, prefixRequested = false)

        SubagentAgentDetailSheet(
            title = title,
            accentColor = SubagentColorPalette.color(SubagentLabelParser.parse(title).nickname),
            statusText = statusTextForSheet,
            modelTitle = if (agent.modelIsRequestedHint) "Requested model" else "Model",
            modelLabel = modelLabel,
            instructionText = trimmedValue(agent.prompt) ?: trimmedValue(action.prompt),
            latestUpdateText = trimmedValue(agent.fallbackMessage),
            onOpen = if (onOpenSubagentThread != null && agent.threadId != null) {
                {
                    selectedAgentDetails = null
                    onOpenSubagentThread(agent.threadId!!)
                }
            } else {
                null
            },
            onDismiss = { selectedAgentDetails = null }
        )
    }
}

// ---------------------------------------------------------------------------
// MARK: - Agent row
// ---------------------------------------------------------------------------

@Composable
private fun SubagentAgentRow(
    title: String,
    status: SubagentStatusPresentation,
    statusText: String,
    modelLabel: String?,
    showsDetails: Boolean,
    onShowDetails: (() -> Unit)?,
    onOpen: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main row content (tappable to open thread)
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Styled label + status text
            Text(
                text = buildAnnotatedString {
                    val parts = SubagentLabelParser.parse(title)
                    withStyle(
                        SpanStyle(
                            color = SubagentColorPalette.color(parts.nickname),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(parts.nickname)
                    }
                    // Role suffix uses onSurface (matching iOS .primary)
                    append(parts.roleSuffix)
                    append(" ")
                    // Status text uses the tone color
                    withStyle(SpanStyle(color = status.tone.color)) {
                        append(statusText)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (!modelLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (onOpen != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Info button
        if (showsDetails && onShowDetails != null) {
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onShowDetails,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Details",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MARK: - Detail sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubagentAgentDetailSheet(
    title: String,
    accentColor: Color,
    statusText: String,
    modelTitle: String,
    modelLabel: String?,
    instructionText: String?,
    latestUpdateText: String?,
    onOpen: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            Text(
                text = SubagentLabelParser.styledText(
                    title = title,
                    roleSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Status section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Model
            if (!modelLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                DetailSection(
                    title = modelTitle,
                    value = modelLabel,
                    monospace = true
                )
            }

            // Instructions
            if (!instructionText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                DetailSection(title = "Instructions", value = instructionText)
            }

            // Latest update
            if (!latestUpdateText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                DetailSection(title = "Latest update", value = latestUpdateText)
            }

            // Empty state
            if (instructionText.isNullOrBlank() && latestUpdateText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "No extra details yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Open button
            if (onOpen != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Open child thread",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    value: String,
    monospace: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------------
// MARK: - Typing indicator
// ---------------------------------------------------------------------------

@Composable
private fun SubagentTypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    }
}

// ---------------------------------------------------------------------------
// MARK: - Label parser
// ---------------------------------------------------------------------------

/**
 * Parses "Locke [explorer]" into (nickname: "Locke", roleSuffix: " (explorer)").
 * Used by both timeline rows and sidebar to produce consistent styled text.
 */
object SubagentLabelParser {
    data class ParsedLabel(val nickname: String, val roleSuffix: String)

    fun parse(title: String): ParsedLabel {
        if (!title.endsWith("]")) return ParsedLabel(title, "")
        val openBracket = title.lastIndexOf('[')
        if (openBracket < 0) return ParsedLabel(title, "")

        val nickname = title.substring(0, openBracket).trim()
        val role = title.substring(openBracket + 1, title.length - 1).trim()

        if (role.isEmpty()) {
            return ParsedLabel(nickname.ifEmpty { title }, "")
        }

        val resolvedName = nickname.ifEmpty {
            role.replaceFirstChar { it.uppercase() }
        }
        return ParsedLabel(resolvedName, " ($role)")
    }

    /** Color derived from the parsed nickname -- consistent across sidebar and timeline. */
    fun nicknameColor(title: String): Color =
        SubagentColorPalette.color(parse(title).nickname)

    /** Build an [AnnotatedString] with the nickname colored and role suffix secondary. */
    fun styledText(
        title: String,
        nicknameColor: Color? = null,
        roleSuffixColor: Color = Color.Gray
    ): AnnotatedString {
        val parts = parse(title)
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = nicknameColor ?: SubagentColorPalette.color(parts.nickname),
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(parts.nickname)
            }
            withStyle(SpanStyle(color = roleSuffixColor)) {
                append(parts.roleSuffix)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MARK: - Color palette
// ---------------------------------------------------------------------------

/** Hash-stable palette -- same nickname always gets the same color. */
object SubagentColorPalette {
    private val colors = listOf(
        Color(0xFFE64D4D), // red
        Color(0xFF4DBF8C), // green
        Color(0xFF668CF2), // blue
        Color(0xFFD99940), // orange
        Color(0xFFB373D9), // purple
        Color(0xFF40C7D1), // teal
        Color(0xFFE68099), // pink
        Color(0xFFA6BF4D), // lime
    )

    fun color(name: String): Color {
        var hash = 5381UL
        for (byte in name.encodeToByteArray()) {
            hash = ((hash shl 5) + hash) + byte.toUByte().toULong()
        }
        return colors[(hash % colors.size.toULong()).toInt()]
    }
}

// ---------------------------------------------------------------------------
// MARK: - Status models
// ---------------------------------------------------------------------------

data class SubagentStatusPresentation(val rawStatus: String?) {
    val normalized: String
        get() = rawStatus
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            ?: "unknown"

    val label: String
        get() = when (normalized) {
            "running", "inprogress" -> "running"
            "completed", "done", "finished", "success" -> "completed"
            "failed", "error", "errored" -> "failed"
            "stopped", "cancelled", "canceled", "interrupted" -> "stopped"
            "queued", "pending" -> "queued"
            else -> "idle"
        }

    val tone: SubagentStatusTone
        get() = when (label) {
            "running" -> SubagentStatusTone.RUNNING
            "completed" -> SubagentStatusTone.COMPLETED
            "failed" -> SubagentStatusTone.FAILED
            "stopped" -> SubagentStatusTone.STOPPED
            else -> SubagentStatusTone.IDLE
        }
}

enum class SubagentStatusTone(val color: Color) {
    RUNNING(Color(0xFF3B82F6)),    // blue
    COMPLETED(StatusGreen),
    FAILED(StatusRed),
    STOPPED(StatusOrange),
    IDLE(Color(0xFF9CA3AF));       // gray
}

// ---------------------------------------------------------------------------
// MARK: - Resolution helpers
// ---------------------------------------------------------------------------

private fun resolvedAgentTitle(agent: CodexSubagentThreadPresentation): String =
    agent.displayLabel

private fun resolvedModelLabel(
    agent: CodexSubagentThreadPresentation,
    threads: List<CodexThread>,
    prefixRequested: Boolean = true
): String? {
    sanitizedSubagentModelLabel(agent.model)?.let { model ->
        return if (agent.modelIsRequestedHint && prefixRequested) "requested: $model" else model
    }

    val thread = threads.firstOrNull { it.id == agent.threadId }
    if (thread != null) {
        sanitizedSubagentModelLabel(thread.model)?.let { return it }
        sanitizedSubagentModelLabel(thread.modelProvider)?.let { return it }
    }

    return null
}

private fun resolvedStatus(
    agent: CodexSubagentThreadPresentation,
    action: CodexSubagentAction,
    runningThreadIDs: Set<String>
): SubagentStatusPresentation {
    if (agent.threadId != null && runningThreadIDs.contains(agent.threadId)) {
        return SubagentStatusPresentation("running")
    }
    if (agent.fallbackStatus == null) {
        return SubagentStatusPresentation(action.status)
    }
    return SubagentStatusPresentation(agent.fallbackStatus)
}

private fun readableStatus(normalizedTool: String, label: String): String = when (normalizedTool) {
    "spawnagent" -> when (label) {
        "running" -> "Starting child thread"
        "completed" -> "Child thread created"
        "failed" -> "Could not create child thread"
        "stopped" -> "Spawn interrupted"
        "queued" -> "Queued for spawn"
        else -> "Preparing child thread"
    }
    "wait", "waitagent" -> when (label) {
        "running" -> "Still working"
        "completed" -> "Finished"
        "failed" -> "Finished with error"
        "stopped" -> "Stopped early"
        "queued" -> "Queued"
        else -> "Waiting for updates"
    }
    "sendinput" -> when (label) {
        "running" -> "Working on new instructions"
        "completed" -> "Processed the update"
        "failed" -> "Update failed"
        "stopped" -> "Update interrupted"
        "queued" -> "Queued update"
        else -> "Instructions sent"
    }
    "resumeagent" -> when (label) {
        "running" -> "Back to work"
        "completed" -> "Resumed and completed"
        "failed" -> "Resume failed"
        "stopped" -> "Resume interrupted"
        "queued" -> "Queued to resume"
        else -> "Resuming agent"
    }
    "closeagent" -> when (label) {
        "running" -> "Closing"
        "completed" -> "Closed"
        "failed" -> "Close failed"
        "stopped" -> "Close interrupted"
        "queued" -> "Queued to close"
        else -> "Closing agent"
    }
    else -> when (label) {
        "running" -> "Working now"
        "completed" -> "Completed"
        "failed" -> "Ended with error"
        "stopped" -> "Stopped"
        "queued" -> "Queued"
        else -> "Idle"
    }
}

private fun detailText(
    agent: CodexSubagentThreadPresentation,
    action: CodexSubagentAction
): String? {
    val sections = mutableListOf<String>()
    val prompt = trimmedValue(agent.prompt) ?: trimmedValue(action.prompt)
    if (prompt != null) sections.add(prompt)

    val statusMessage = trimmedValue(agent.fallbackMessage)
    if (statusMessage != null && statusMessage !in sections) {
        sections.add(if (sections.isEmpty()) statusMessage else "Latest update: $statusMessage")
    }

    return sections.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
}

private fun trimmedValue(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

private fun sanitizedSubagentModelLabel(value: String?): String? {
    val trimmed = trimmedValue(value) ?: return null
    if (trimmed.equals("openai", ignoreCase = true)) return null
    return trimmed
}
