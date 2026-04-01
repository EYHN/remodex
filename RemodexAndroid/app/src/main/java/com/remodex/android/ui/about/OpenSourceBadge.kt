package com.remodex.android.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remodex.android.R

enum class OpenSourceBadgeStyle { Light, Dark }

private const val REPO_URL = "https://github.com/Emanuele-web04/remodex"

/**
 * Capsule badge that opens the GitHub repo when tapped.
 * Two visual styles: [Light] for dark backgrounds, [Dark] for light backgrounds.
 */
@Composable
fun OpenSourceBadge(
    style: OpenSourceBadgeStyle = OpenSourceBadgeStyle.Light,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val foreground = when (style) {
        OpenSourceBadgeStyle.Light ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        OpenSourceBadgeStyle.Dark ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = when (style) {
        OpenSourceBadgeStyle.Light ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        OpenSourceBadgeStyle.Dark ->
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when (style) {
        OpenSourceBadgeStyle.Light ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        OpenSourceBadgeStyle.Dark ->
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }

    val capsuleShape = RoundedCornerShape(50)

    Surface(
        color = background,
        shape = capsuleShape,
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = capsuleShape)
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))
                )
            }
            .semantics { contentDescription = "Open source on GitHub" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.github_mark_white),
                contentDescription = null,
                colorFilter = ColorFilter.tint(foreground),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Open source",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = foreground,
            )
        }
    }
}
