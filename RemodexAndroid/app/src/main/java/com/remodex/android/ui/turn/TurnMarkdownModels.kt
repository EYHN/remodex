package com.remodex.android.ui.turn

/**
 * Markdown rendering profile used to configure how content is
 * displayed in different turn contexts (assistant prose vs. file-change system text).
 */
enum class MarkdownRenderProfile(val cacheKey: String) {
    AssistantProse("assistantProse"),
    FileChangeSystem("fileChangeSystem");
}

/**
 * Controls how skill/tool references embedded in assistant text are replaced
 * before the final markdown is rendered.
 */
enum class SkillReferenceReplacementStyle {
    MentionToken,
    DisplayName;
}
