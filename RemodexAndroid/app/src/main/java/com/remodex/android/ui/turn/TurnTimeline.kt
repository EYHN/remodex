package com.remodex.android.ui.turn

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.R
import com.remodex.android.data.model.CodexImageAttachment
import com.remodex.android.data.model.AssistantRevertPresentation
import com.remodex.android.data.model.CodexMessage
import com.remodex.android.data.model.JsonValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TurnAutoScrollMode {
    FollowBottom,
    Manual
}

@Composable
fun TurnTimeline(
    threadId: String? = null,
    messages: List<CodexMessage>,
    isRunning: Boolean,
    isHistoryLoading: Boolean = false,
    suppressEmptyState: Boolean = false,
    assistantRevertPresentationForMessage: ((CodexMessage) -> AssistantRevertPresentation?)? = null,
    assistantDiffAvailableForMessage: ((CodexMessage) -> Boolean)? = null,
    onOpenAssistantDiff: ((CodexMessage) -> Unit)? = null,
    onOpenAssistantRevert: ((CodexMessage) -> Unit)? = null,
    onSubmitStructuredUserInput: ((JsonValue, Map<String, List<String>>) -> Unit)? = null,
    onOpenSubagentThread: ((String) -> Unit)? = null,
    onRetryUserMessage: ((String, List<CodexImageAttachment>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    key(threadId) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val showRunningIndicator = isRunning && messages.lastOrNull()?.isStreaming != true
        var autoScrollMode by remember { mutableStateOf(TurnAutoScrollMode.FollowBottom) }
        var isUserScrolling by remember { mutableStateOf(false) }
        var isProgrammaticScrollInProgress by remember { mutableStateOf(false) }
        var userScrollCooldownUntilMs by remember { mutableStateOf(0L) }
        var needsInitialBottomSnap by remember { mutableStateOf(true) }

        val isAtBottom by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                if (layoutInfo.totalItemsCount == 0) {
                    true
                } else {
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                    lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
                }
            }
        }

        val isScrollCooldownActive by remember {
            derivedStateOf {
                userScrollCooldownUntilMs > 0L && SystemClock.elapsedRealtime() < userScrollCooldownUntilMs
            }
        }

        val bottomAnchorIndex = messages.size + if (showRunningIndicator) 1 else 0

        LaunchedEffect(threadId) {
            autoScrollMode = TurnAutoScrollMode.FollowBottom
            isUserScrolling = false
            isProgrammaticScrollInProgress = false
            userScrollCooldownUntilMs = 0L
            needsInitialBottomSnap = true
        }

        LaunchedEffect(listState.isScrollInProgress, isAtBottom, isProgrammaticScrollInProgress) {
            if (listState.isScrollInProgress) {
                if (!isProgrammaticScrollInProgress) {
                    isUserScrolling = true
                    if (!isAtBottom) {
                        autoScrollMode = TurnAutoScrollMode.Manual
                    }
                    userScrollCooldownUntilMs = 0L
                }
            } else if (!isProgrammaticScrollInProgress) {
                if (isAtBottom) {
                    isUserScrolling = false
                    if (!isScrollCooldownActive) {
                        autoScrollMode = TurnAutoScrollMode.FollowBottom
                    }
                } else if (isUserScrolling) {
                    isUserScrolling = false
                    userScrollCooldownUntilMs = SystemClock.elapsedRealtime() + 250L
                }
            }
        }

        LaunchedEffect(userScrollCooldownUntilMs, isAtBottom) {
            val cooldownUntil = userScrollCooldownUntilMs
            if (cooldownUntil <= 0L || !isAtBottom) {
                return@LaunchedEffect
            }

            val delayMs = cooldownUntil - SystemClock.elapsedRealtime()
            if (delayMs > 0L) {
                delay(delayMs)
            }

            if (SystemClock.elapsedRealtime() >= userScrollCooldownUntilMs && isAtBottom) {
                autoScrollMode = TurnAutoScrollMode.FollowBottom
                userScrollCooldownUntilMs = 0L
            }
        }

        LaunchedEffect(
            threadId,
            bottomAnchorIndex,
            messages.lastOrNull()?.id,
            messages.lastOrNull()?.text,
            messages.lastOrNull()?.isStreaming,
            messages.size,
            isRunning,
            autoScrollMode,
            isScrollCooldownActive,
            needsInitialBottomSnap
        ) {
            if (messages.isEmpty() ||
                autoScrollMode != TurnAutoScrollMode.FollowBottom ||
                isScrollCooldownActive
            ) {
                return@LaunchedEffect
            }

            isProgrammaticScrollInProgress = true
            try {
                if (needsInitialBottomSnap ||
                    messages.lastOrNull()?.isStreaming == true ||
                    showRunningIndicator
                ) {
                    listState.scrollToItem(bottomAnchorIndex)
                } else {
                    listState.animateScrollToItem(bottomAnchorIndex)
                }
                needsInitialBottomSnap = false
            } finally {
                isProgrammaticScrollInProgress = false
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (suppressEmptyState) {
                    Spacer(modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isHistoryLoading) {
                            LoadingConversationRow()
                        } else if (isRunning) {
                            RowWithSpinner()
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                tonalElevation = 1.dp
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.remodex_app_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                text = "Hi! How can I help you?",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Chats are End-to-end encrypted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        MessageRow(
                            message = message,
                            assistantRevertPresentation = assistantRevertPresentationForMessage?.invoke(message),
                            isAssistantDiffAvailable = assistantDiffAvailableForMessage?.invoke(message) == true,
                            onOpenAssistantDiff = onOpenAssistantDiff?.let { callback -> { callback(message) } },
                            onOpenAssistantRevert = onOpenAssistantRevert?.let { callback -> { callback(message) } },
                            onSubmitStructuredUserInput = onSubmitStructuredUserInput,
                            onOpenSubagentThread = onOpenSubagentThread,
                            onRetryUserMessage = onRetryUserMessage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (showRunningIndicator) {
                        item(key = "streaming_indicator") {
                            RowWithSpinner(modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    item(key = "bottom_anchor") {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (!isAtBottom) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 22.dp)
                            .shadow(elevation = 4.dp, shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable {
                                autoScrollMode = TurnAutoScrollMode.FollowBottom
                                isUserScrolling = false
                                userScrollCooldownUntilMs = 0L
                                needsInitialBottomSnap = false
                                coroutineScope.launch {
                                    isProgrammaticScrollInProgress = true
                                    try {
                                        listState.scrollToItem(bottomAnchorIndex)
                                    } finally {
                                        isProgrammaticScrollInProgress = false
                                    }
                                }
                            },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 3.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to latest",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowWithSpinner(modifier: Modifier = Modifier) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Thinking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingConversationRow(modifier: Modifier = Modifier) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        modifier = modifier
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Loading conversation...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
