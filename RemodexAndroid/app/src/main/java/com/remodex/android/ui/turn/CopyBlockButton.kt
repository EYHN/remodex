package com.remodex.android.ui.turn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Small floating "Copy" button intended to overlay code blocks.
 *
 * Mirrors the iOS `CopyBlockButton`: shows a clipboard icon that
 * briefly switches to a checkmark after the text is copied.
 * Position this at the top-end corner of the code block container.
 */
@Composable
fun CopyBlockButton(
    text: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopiedFeedback by remember { mutableStateOf(false) }

    if (showCopiedFeedback) {
        LaunchedEffect(Unit) {
            delay(1500L)
            showCopiedFeedback = false
        }
    }

    Surface(
        onClick = {
            clipboardManager.setText(AnnotatedString(text))
            showCopiedFeedback = true
        },
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = showCopiedFeedback,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "copy-icon"
            ) { copied ->
                if (copied) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Copied",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedContent(
                targetState = showCopiedFeedback,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "copy-label"
            ) { copied ->
                if (copied) {
                    Row {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copied",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // Empty placeholder so AnimatedContent keeps consistent sizing
                    Spacer(modifier = Modifier.width(0.dp))
                }
            }
        }
    }
}
