package com.remodex.android.ui.sidebar

object SidebarThreadsLoadingPresentation {
    // Keeps pull-to-refresh from stacking a second spinner over an already populated sidebar.
    fun shouldShowOverlay(
        isLoadingThreads: Boolean,
        threadCount: Int
    ): Boolean = isLoadingThreads && threadCount == 0
}
