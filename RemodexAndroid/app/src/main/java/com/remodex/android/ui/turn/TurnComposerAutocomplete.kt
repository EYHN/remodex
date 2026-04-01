package com.remodex.android.ui.turn

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.remodex.android.data.model.CodexFuzzyFileMatch
import com.remodex.android.data.model.CodexSkillMetadata
import java.util.UUID

data class ComposerMentionedFile(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val path: String
)

data class ComposerMentionedSkill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String? = null,
    val description: String? = null
)

enum class ComposerSlashCommand(
    val token: String,
    val title: String,
    val subtitle: String,
    val cannedPrompt: String? = null
) {
    REVIEW(
        token = "/review",
        title = "Code Review",
        subtitle = "Run the reviewer on local changes or a base branch"
    ),
    FORK(
        token = "/fork",
        title = "Fork",
        subtitle = "Fork this thread into local or a new worktree"
    ),
    STATUS(
        token = "/status",
        title = "Status",
        subtitle = "Show context usage and rate limits"
    ),
    SUBAGENTS(
        token = "/subagents",
        title = "Subagents",
        subtitle = "Insert a delegation prompt",
        cannedPrompt = "Run subagents for different tasks. Delegate distinct work in parallel when helpful and then synthesize the results."
    );

    companion object {
        fun filtered(
            query: String,
            commands: List<ComposerSlashCommand> = entries
        ): List<ComposerSlashCommand> {
            val normalizedQuery = query.trim().lowercase()
            if (normalizedQuery.isEmpty()) {
                return commands
            }

            return commands.filter { command ->
                command.token.contains(normalizedQuery) ||
                    command.title.lowercase().contains(normalizedQuery) ||
                    command.subtitle.lowercase().contains(normalizedQuery)
            }
        }

        fun availableCommands(allowsForkCommand: Boolean): List<ComposerSlashCommand> =
            entries.filter { command ->
                when (command) {
                    FORK -> allowsForkCommand
                    REVIEW, STATUS, SUBAGENTS -> true
                }
            }
    }
}

enum class ComposerForkDestination(
    val title: String,
    val subtitle: String
) {
    LOCAL(
        title = "Fork into local",
        subtitle = "Continue in a new local thread"
    ),
    NEW_WORKTREE(
        title = "Fork into new worktree",
        subtitle = "Create a fresh worktree and fork into it"
    )
}

enum class ComposerSlashAutocompleteMode {
    COMMANDS,
    REVIEW_TARGETS,
    FORK_DESTINATIONS
}

enum class ComposerReviewTarget(
    val title: String,
    val subtitle: String
) {
    UNCOMMITTED_CHANGES(
        title = "Uncommitted changes",
        subtitle = "Review the current local modifications in this repo"
    ),
    BASE_BRANCH(
        title = "Base branch",
        subtitle = "Review the current work against a chosen base branch"
    )
}

data class ComposerTrailingToken(
    val query: String,
    val tokenRange: IntRange
)

fun trailingFileAutocompleteToken(text: String): ComposerTrailingToken? {
    if (text.isEmpty() || text.last().isWhitespace()) return null
    val triggerIndex = text.lastIndexOf('@')
    if (triggerIndex < 0) return null
    if (triggerIndex > 0 && !text[triggerIndex - 1].isWhitespace()) return null

    val rawQuery = text.substring(triggerIndex + 1)
    val query = rawQuery.trim()
    if (query.isEmpty() || query.contains('\n')) return null

    val hasWhitespace = query.any { it.isWhitespace() }
    if (hasWhitespace && !query.contains('/') && !query.contains('\\') && !query.contains('.')) {
        return null
    }

    return ComposerTrailingToken(query = query, tokenRange = triggerIndex until text.length)
}

fun trailingSkillAutocompleteToken(text: String): ComposerTrailingToken? =
    trailingToken(text, '$')

fun trailingSlashCommandToken(text: String): ComposerTrailingToken? {
    if (text.isEmpty()) return null

    val tokenStart = text.indexOfLast { it.isWhitespace() }
        .let { if (it >= 0) it + 1 else 0 }
    if (tokenStart >= text.length || text[tokenStart] != '/') {
        return null
    }

    val query = text.substring(tokenStart + 1)
    if (query.any { it.isWhitespace() }) {
        return null
    }

    return ComposerTrailingToken(query = query, tokenRange = tokenStart until text.length)
}

fun replacingTrailingToken(
    text: String,
    token: ComposerTrailingToken?,
    replacement: String
): String? {
    token ?: return null
    val normalizedReplacement = replacement.trim()
    if (normalizedReplacement.isEmpty()) {
        return null
    }

    val builder = StringBuilder()
    builder.append(text.substring(0, token.tokenRange.first))
    builder.append(normalizedReplacement)
    return builder.toString().trimEnd()
}

fun removingTrailingToken(
    text: String,
    token: ComposerTrailingToken?
): String? {
    token ?: return null
    return text.substring(0, token.tokenRange.first).trimEnd()
}

fun containsBoundedToken(
    text: String,
    token: String
): Boolean {
    val normalizedToken = token.trim()
    if (normalizedToken.isEmpty()) {
        return false
    }

    val pattern = Regex("(^|\\s)${Regex.escape(normalizedToken)}(?=\\s|$)")
    return pattern.containsMatchIn(text)
}

fun removeBoundedToken(
    text: String,
    token: String
): String {
    val normalizedToken = token.trim()
    if (normalizedToken.isEmpty()) {
        return text
    }

    val pattern = Regex("(^|\\s)${Regex.escape(normalizedToken)}(?=\\s|$)")
    val match = pattern.find(text) ?: return text
    val prefix = match.groups[1]?.value.orEmpty()
    val replaced = text.replaceRange(match.range, prefix)
    return replaced.replace(Regex("\\s{2,}"), " ").trim()
}

fun syncMentionedFiles(
    text: String,
    mentions: List<ComposerMentionedFile>
): List<ComposerMentionedFile> =
    mentions.filter { mention ->
        containsBoundedToken(text, "@${mention.path}")
    }

fun syncMentionedSkills(
    text: String,
    mentions: List<ComposerMentionedSkill>
): List<ComposerMentionedSkill> =
    mentions.filter { mention ->
        containsBoundedToken(text, "\$${mention.name}")
    }

fun filterSkillAutocompleteItems(
    skills: List<CodexSkillMetadata>,
    query: String
): List<CodexSkillMetadata> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
        return skills.take(6)
    }

    return skills
        .filter { skill ->
            skill.enabled &&
                (
                    skill.name.contains(normalizedQuery, ignoreCase = true) ||
                        skill.description.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                        skill.path.orEmpty().contains(normalizedQuery, ignoreCase = true)
                    )
        }
        .take(6)
}

@Composable
fun MentionedFilesRow(
    files: List<ComposerMentionedFile>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    MentionChipRow(
        items = files,
        titleForItem = { "@${it.fileName}" },
        subtitleForItem = { it.path },
        onRemove = onRemove,
        modifier = modifier
    )
}

@Composable
fun MentionedSkillsRow(
    skills: List<ComposerMentionedSkill>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    MentionChipRow(
        items = skills,
        titleForItem = { "\$${it.name}" },
        subtitleForItem = { it.path ?: it.description },
        onRemove = onRemove,
        modifier = modifier
    )
}

@Composable
fun FileAutocompletePanel(
    items: List<CodexFuzzyFileMatch>,
    isLoading: Boolean,
    query: String,
    onSelect: (CodexFuzzyFileMatch) -> Unit,
    modifier: Modifier = Modifier
) {
    ComposerAutocompletePanel(
        modifier = modifier,
        loadingLabel = "Searching files...",
        emptyLabel = "No files for @$query",
        isLoading = isLoading,
        hasItems = items.isNotEmpty()
    ) {
        items.forEach { item ->
            AutocompleteRow(
                title = item.resolvedFileName,
                subtitle = item.path,
                onClick = { onSelect(item) }
            )
        }
    }
}

@Composable
fun SkillAutocompletePanel(
    items: List<CodexSkillMetadata>,
    isLoading: Boolean,
    query: String,
    onSelect: (CodexSkillMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    ComposerAutocompletePanel(
        modifier = modifier,
        loadingLabel = "Searching skills...",
        emptyLabel = "No skills for \$$query",
        isLoading = isLoading,
        hasItems = items.isNotEmpty()
    ) {
        items.forEach { skill ->
            AutocompleteRow(
                title = "\$${skill.name}",
                subtitle = skill.description ?: skill.path.orEmpty(),
                onClick = { onSelect(skill) }
            )
        }
    }
}

@Composable
fun SlashCommandAutocompletePanel(
    mode: ComposerSlashAutocompleteMode,
    commands: List<ComposerSlashCommand>,
    query: String,
    onSelectCommand: (ComposerSlashCommand) -> Unit,
    onSelectReviewTarget: (ComposerReviewTarget) -> Unit,
    onSelectForkDestination: (ComposerForkDestination) -> Unit,
    onDismissSubmenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mode == ComposerSlashAutocompleteMode.REVIEW_TARGETS) {
        ComposerAutocompletePanel(
            modifier = modifier,
            loadingLabel = "",
            emptyLabel = "",
            isLoading = false,
            hasItems = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Code Review",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose what the reviewer should compare.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismissSubmenu,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close review options",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            ComposerReviewTarget.entries.forEach { target ->
                AutocompleteRow(
                    title = target.title,
                    subtitle = target.subtitle,
                    onClick = { onSelectReviewTarget(target) }
                )
            }
        }
    } else if (mode == ComposerSlashAutocompleteMode.FORK_DESTINATIONS) {
        ComposerAutocompletePanel(
            modifier = modifier,
            loadingLabel = "",
            emptyLabel = "",
            isLoading = false,
            hasItems = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fork",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Choose where the new thread should land.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismissSubmenu,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close fork options",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            ComposerForkDestination.entries.forEach { destination ->
                AutocompleteRow(
                    title = destination.title,
                    subtitle = destination.subtitle,
                    onClick = { onSelectForkDestination(destination) }
                )
            }
        }
    } else {
        ComposerAutocompletePanel(
            modifier = modifier,
            loadingLabel = "",
            emptyLabel = "No commands for /$query",
            isLoading = false,
            hasItems = commands.isNotEmpty()
        ) {
            commands.forEach { command ->
                AutocompleteRow(
                    title = command.token,
                    subtitle = command.subtitle,
                    onClick = { onSelectCommand(command) }
                )
            }
        }
    }
}

private fun trailingToken(text: String, trigger: Char): ComposerTrailingToken? {
    if (text.isEmpty()) return null

    val tokenStart = text.indexOfLast { it.isWhitespace() }
        .let { if (it >= 0) it + 1 else 0 }
    if (tokenStart >= text.length || text[tokenStart] != trigger) {
        return null
    }

    val query = text.substring(tokenStart + 1)
    if (query.isEmpty() || query.any { it.isWhitespace() }) {
        return null
    }

    return ComposerTrailingToken(query = query, tokenRange = tokenStart until text.length)
}

@Composable
private fun <T> MentionChipRow(
    items: List<T>,
    titleForItem: (T) -> String,
    subtitleForItem: (T) -> String?,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) where T : Any {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val itemId = when (item) {
                is ComposerMentionedFile -> item.id
                is ComposerMentionedSkill -> item.id
                else -> return@forEach
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.width(180.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = titleForItem(item),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        subtitleForItem(item)?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onRemove(itemId) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove mention",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerAutocompletePanel(
    loadingLabel: String,
    emptyLabel: String,
    isLoading: Boolean,
    hasItems: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                isLoading -> {
                    Text(
                        text = loadingLabel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                !hasItems -> {
                    Text(
                        text = emptyLabel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> content()
            }
        }
    }
}

@Composable
private fun AutocompleteRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
