package com.remodex.android.ui.turn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexAccessMode
import com.remodex.android.data.model.ContextWindowUsage

private val StatusPillBackground = Color.White.copy(alpha = 0.94f)
private val StatusPillBorder = Color(0x14000000)
private val StatusPillSecondaryGray = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnComposerStatusBar(
    isComposerFocused: Boolean,
    isWorktreeProject: Boolean,
    isRuntimeSelectorEnabled: Boolean,
    onOpenRuntimeActions: () -> Unit,
    selectedAccessMode: CodexAccessMode,
    onSelectAccessMode: (CodexAccessMode) -> Unit,
    currentBranch: String?,
    isBranchSelectorEnabled: Boolean,
    onOpenBranchSelector: () -> Unit,
    contextWindowUsage: ContextWindowUsage?,
    onOpenStatusSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isComposerFocused,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    icon = {
                        Icon(
                            imageVector = if (isWorktreeProject) {
                                Icons.Default.AccountTree
                            } else {
                                Icons.Default.LaptopMac
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = StatusPillSecondaryGray
                        )
                    },
                    label = if (isWorktreeProject) "Worktree" else "Local",
                    enabled = isRuntimeSelectorEnabled,
                    onClick = onOpenRuntimeActions
                )

                AccessModePill(
                    selectedAccessMode = selectedAccessMode,
                    onSelectAccessMode = onSelectAccessMode
                )

                currentBranch?.takeIf { it.isNotBlank() }?.let { branch ->
                    TurnGitBranchSelector(
                        currentBranch = branch,
                        isLoading = false,
                        isSwitching = false,
                        enabled = isBranchSelectorEnabled,
                        onClick = onOpenBranchSelector
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    modifier = Modifier.clickable(onClick = onOpenStatusSheet),
                    shape = RoundedCornerShape(999.dp),
                    color = StatusPillBackground,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        StatusPillBorder
                    )
                ) {
                    ContextWindowProgressRing(
                        usage = contextWindowUsage,
                        size = 24.dp,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessModePill(
    selectedAccessMode: CodexAccessMode,
    onSelectAccessMode: (CodexAccessMode) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val tint = if (selectedAccessMode == CodexAccessMode.FULL_ACCESS) {
        Color(0xFFD97706)
    } else {
        StatusPillSecondaryGray
    }

    Box {
        Surface(
            modifier = Modifier.clickable { isMenuOpen = true },
            shape = RoundedCornerShape(999.dp),
            color = StatusPillBackground,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                StatusPillBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (selectedAccessMode == CodexAccessMode.FULL_ACCESS) {
                        Icons.Default.WarningAmber
                    } else {
                        Icons.Default.Shield
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = tint
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = tint
                )
            }
        }

        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = { isMenuOpen = false }
        ) {
            CodexAccessMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayLabel) },
                    leadingIcon = if (mode == selectedAccessMode) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onSelectAccessMode(mode)
                        isMenuOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClick = onClick)
        } else {
            Modifier
        },
        shape = RoundedCornerShape(999.dp),
        color = StatusPillBackground,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            StatusPillBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = StatusPillSecondaryGray
            )
        }
    }
}
