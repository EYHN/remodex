package com.remodex.android.ui.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexStructuredUserInputOption
import com.remodex.android.data.model.CodexStructuredUserInputQuestion
import com.remodex.android.data.model.CodexStructuredUserInputRequest
import com.remodex.android.data.model.JsonValue
import com.remodex.android.ui.theme.AccentPlan

@Composable
fun StructuredUserInputCard(
    request: CodexStructuredUserInputRequest,
    onSubmit: (JsonValue, Map<String, List<String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOptionsByQuestionId = remember(request.requestIdKey) { mutableStateMapOf<String, String>() }
    val typedAnswersByQuestionId = remember(request.requestIdKey) { mutableStateMapOf<String, String>() }
    var hasSubmittedResponse by remember(request.requestIdKey) { mutableStateOf(false) }

    fun resolvedAnswer(question: CodexStructuredUserInputQuestion): String? {
        val typed = typedAnswersByQuestionId[question.id]?.trim()
        if (!typed.isNullOrEmpty()) {
            return typed
        }

        val selected = selectedOptionsByQuestionId[question.id]?.trim()
        if (!selected.isNullOrEmpty()) {
            return selected
        }

        return null
    }

    val isSubmitEnabled = !hasSubmittedResponse && request.questions.all { question ->
        resolvedAnswer(question) != null
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = AccentPlan.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            AccentPlan.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Input required",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPlan
            )

            request.questions.forEach { question ->
                StructuredQuestionBlock(
                    question = question,
                    selectedOption = selectedOptionsByQuestionId[question.id],
                    typedAnswer = typedAnswersByQuestionId[question.id].orEmpty(),
                    isEnabled = !hasSubmittedResponse,
                    onSelectOption = { selectedOptionsByQuestionId[question.id] = it },
                    onTypedAnswerChange = { typedAnswersByQuestionId[question.id] = it }
                )
            }

            Button(
                onClick = {
                    hasSubmittedResponse = true
                    onSubmit(
                        request.requestID,
                        request.questions.associate { question ->
                            question.id to listOfNotNull(resolvedAnswer(question))
                        }
                    )
                },
                enabled = isSubmitEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPlan,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (hasSubmittedResponse) "Sent" else "Send")
            }
        }
    }
}

@Composable
private fun StructuredQuestionBlock(
    question: CodexStructuredUserInputQuestion,
    selectedOption: String?,
    typedAnswer: String,
    isEnabled: Boolean,
    onSelectOption: (String) -> Unit,
    onTypedAnswerChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.header
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { header ->
                Text(
                    text = header.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

        Text(
            text = question.question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        question.options.forEach { option ->
            StructuredOptionRow(
                option = option,
                isSelected = selectedOption == option.label,
                isEnabled = isEnabled,
                onSelect = { onSelectOption(option.label) }
            )
        }

        if (question.isOther || question.options.isEmpty()) {
            OutlinedTextField(
                value = typedAnswer,
                onValueChange = onTypedAnswerChange,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (question.isSecret) "Enter response" else "Type your answer"
                    )
                },
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (question.isSecret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (question.isSecret) KeyboardType.Password else KeyboardType.Text
                )
            )
        }
    }
}

@Composable
private fun StructuredOptionRow(
    option: CodexStructuredUserInputOption,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) AccentPlan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) AccentPlan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isEnabled, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) AccentPlan else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            option.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }
    }
}
