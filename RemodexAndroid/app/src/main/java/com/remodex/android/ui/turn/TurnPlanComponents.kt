package com.remodex.android.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.CodexPlanStepStatus
import com.remodex.android.ui.theme.AccentBlue
import com.remodex.android.ui.theme.AccentPlan
import com.remodex.android.ui.theme.RemodexGray400
import com.remodex.android.ui.theme.StatusGreen

@Composable
fun PlanExecutionAccessory(
    message: CodexMessage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = message.planState?.steps.orEmpty()
    val activeCount = steps.count { it.status != CodexPlanStepStatus.COMPLETED }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = AccentPlan.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            AccentPlan.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Active plan",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            message.planState?.explanation
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { explanation ->
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            if (steps.isNotEmpty()) {
                Text(
                    text = if (activeCount == 0) {
                        "${steps.size} steps complete"
                    } else {
                        "$activeCount of ${steps.size} steps still active"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentPlan
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanExecutionSheet(
    message: CodexMessage,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Active plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            message.planState?.explanation
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { explanation ->
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            message.planState?.steps.orEmpty().forEach { step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val icon = when (step.status) {
                        CodexPlanStepStatus.COMPLETED -> Icons.Default.CheckCircle
                        CodexPlanStepStatus.IN_PROGRESS -> Icons.Default.RadioButtonChecked
                        CodexPlanStepStatus.PENDING -> Icons.Default.RadioButtonUnchecked
                    }
                    val tint = when (step.status) {
                        CodexPlanStepStatus.COMPLETED -> StatusGreen
                        CodexPlanStepStatus.IN_PROGRESS -> AccentBlue
                        CodexPlanStepStatus.PENDING -> RemodexGray400
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(18.dp),
                        tint = tint
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = step.step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (step.status) {
                                CodexPlanStepStatus.COMPLETED -> "Completed"
                                CodexPlanStepStatus.IN_PROGRESS -> "In progress"
                                CodexPlanStepStatus.PENDING -> "Pending"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = tint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
