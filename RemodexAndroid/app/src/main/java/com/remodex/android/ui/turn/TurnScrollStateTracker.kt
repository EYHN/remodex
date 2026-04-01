package com.remodex.android.ui.turn

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/**
 * Tracks scroll position state for the chat timeline.
 * Matches iOS TurnScrollStateTracker.
 *
 * - Auto-anchors to the bottom when new messages arrive (streaming/new assistant rows).
 * - Disengages auto-anchor when the user scrolls up.
 * - Re-engages when the user scrolls back to the bottom.
 */
class TurnScrollStateTracker(
    val listState: LazyListState
) {
    // Whether we should auto-scroll to bottom on content changes
    var isAnchoredToBottom by mutableStateOf(true)
        private set

    // Whether the user has manually scrolled away from the bottom
    val isUserScrolledUp: Boolean
        get() = !isAnchoredToBottom

    // Call this when the user performs a manual scroll gesture
    fun onUserScroll() {
        // Check if we're near the bottom (within threshold)
        val isNearBottom = isNearBottom()
        isAnchoredToBottom = isNearBottom
    }

    // Call to force re-anchor to bottom (e.g., user taps "scroll to bottom" button)
    fun anchorToBottom() {
        isAnchoredToBottom = true
    }

    // Call when switching threads to reset state
    fun reset() {
        isAnchoredToBottom = true
    }

    private fun isNearBottom(threshold: Int = 3): Boolean {
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) return true
        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
        return lastVisibleItem >= totalItems - threshold
    }
}

@Composable
fun rememberTurnScrollStateTracker(
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
): TurnScrollStateTracker {
    val tracker = remember(listState) { TurnScrollStateTracker(listState) }

    // Observe scroll state changes
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            tracker.onUserScroll()
        }
    }

    // Auto-check anchor when scroll settles
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!listState.isScrollInProgress) {
            tracker.onUserScroll()
        }
    }

    return tracker
}
