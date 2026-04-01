package com.remodex.android.ui.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.AIFileChange
import com.remodex.android.data.model.AssistantRevertPresentation
import com.remodex.android.data.model.RevertPreviewResult

data class AssistantRevertSheetState(
    val changeSetId: String,
    val workingDirectory: String,
    val presentation: AssistantRevertPresentation,
    val fileChanges: List<AIFileChange>,
    val preview: RevertPreviewResult? = null,
    val isLoadingPreview: Boolean = true,
    val isApplying: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantRevertSheet(
    state: AssistantRevertSheetState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val totalAdditions = state.fileChanges.sumOf { it.additions }
    val totalDeletions = state.fileChanges.sumOf { it.deletions }
    val canConfirm = state.preview?.canRevert == true && !state.isLoadingPreview && !state.isApplying

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Undo this response",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            SheetCard {
                Text(
                    text = "This action will try to undo only the changes from this response. Later local edits stay untouched unless they overlap.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${state.fileChanges.size} file${if (state.fileChanges.size == 1) "" else "s"}  +$totalAdditions  -$totalDeletions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.fileChanges.forEach { fileChange ->
                    Text(
                        text = fileChange.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                state.presentation.helperText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.presentation.warningText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            when {
                state.isLoadingPreview -> {
                    SheetCard {
                        Text(
                            text = "Checking whether the reverse patch applies cleanly...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                state.preview != null -> {
                    SheetCard {
                        Text(
                            text = if (state.preview.canRevert) {
                                "This response can be undone cleanly."
                            } else {
                                "Could not safely undo this response."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        state.preview.stagedFiles.forEach {
                            Text(
                                text = "$it: Unstage this file first to keep revert predictable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.preview.unsupportedReasons.forEach {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.preview.conflicts.forEach {
                            Text(
                                text = "${it.path}: ${it.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Red.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isApplying) "Undoing..." else "Undo changes")
            }
        }
    }
}

@Composable
private fun SheetCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
