package com.remodex.android.ui.turn

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import kotlin.math.roundToInt

data class MermaidMarkdownContent(
    val segments: List<MermaidMarkdownSegment>
) {
    val hasMermaidBlocks: Boolean
        get() = segments.any { it.kind == MermaidMarkdownSegmentKind.MERMAID }
}

data class MermaidMarkdownSegment(
    val id: String,
    val kind: MermaidMarkdownSegmentKind,
    val text: String
)

enum class MermaidMarkdownSegmentKind {
    MARKDOWN,
    MERMAID
}

object MermaidMarkdownParser {
    private val mermaidFenceRegex = Regex("""```mermaid\s*(.*?)```""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun parse(messageId: String, text: String): MermaidMarkdownContent {
        val matches = mermaidFenceRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            return MermaidMarkdownContent(
                segments = listOf(
                    MermaidMarkdownSegment(
                        id = "$messageId-markdown-0",
                        kind = MermaidMarkdownSegmentKind.MARKDOWN,
                        text = text
                    )
                )
            )
        }

        val segments = mutableListOf<MermaidMarkdownSegment>()
        var cursor = 0
        var index = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                val markdown = text.substring(cursor, match.range.first)
                if (markdown.isNotBlank()) {
                    segments += MermaidMarkdownSegment(
                        id = "$messageId-markdown-${index++}",
                        kind = MermaidMarkdownSegmentKind.MARKDOWN,
                        text = markdown
                    )
                }
            }

            val source = match.groupValues.getOrNull(1).orEmpty().trim()
            segments += MermaidMarkdownSegment(
                id = "$messageId-mermaid-${index++}",
                kind = MermaidMarkdownSegmentKind.MERMAID,
                text = source
            )
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            val trailingMarkdown = text.substring(cursor)
            if (trailingMarkdown.isNotBlank()) {
                segments += MermaidMarkdownSegment(
                    id = "$messageId-markdown-${index}",
                    kind = MermaidMarkdownSegmentKind.MARKDOWN,
                    text = trailingMarkdown
                )
            }
        }

        return MermaidMarkdownContent(
            segments = if (segments.isEmpty()) {
                listOf(
                    MermaidMarkdownSegment(
                        id = "$messageId-markdown-empty",
                        kind = MermaidMarkdownSegmentKind.MARKDOWN,
                        text = text
                    )
                )
            } else {
                segments
            }
        )
    }
}

@Composable
fun AssistantMarkdownContent(
    messageId: String,
    text: String,
    markwon: Markwon,
    bodyColor: Int,
    modifier: Modifier = Modifier
) {
    val content = remember(messageId, text) {
        MermaidMarkdownParser.parse(messageId, text)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content.segments.forEach { segment ->
            when (segment.kind) {
                MermaidMarkdownSegmentKind.MARKDOWN -> MarkdownSegmentView(
                    text = segment.text,
                    markwon = markwon,
                    bodyColor = bodyColor
                )
                MermaidMarkdownSegmentKind.MERMAID -> MermaidDiagramView(source = segment.text)
            }
        }
    }
}

@Composable
private fun MarkdownSegmentView(
    text: String,
    markwon: Markwon,
    bodyColor: Int
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { viewContext ->
            android.widget.TextView(viewContext).apply {
                setTextColor(bodyColor)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            textView.setTextColor(bodyColor)
            markwon.setMarkdown(textView, text)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MermaidDiagramView(source: String) {
    var contentHeightPx by remember(source) { mutableIntStateOf(360) }
    var errorMessage by remember(source) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val contentHeightDp = with(density) {
        contentHeightPx.coerceIn(320, 1200).toDp()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp)
    ) {
        if (errorMessage != null) {
            Text(
                text = "Diagram couldn't be rendered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            AndroidView(
                factory = { context ->
                    createMermaidWebView(
                        context = context,
                        source = source,
                        onHeightResolved = { contentHeightPx = it },
                        onRenderError = { errorMessage = it }
                    )
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        mermaidHtml(source),
                        "text/html",
                        "utf-8",
                        null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = contentHeightDp)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMermaidWebView(
    context: Context,
    source: String,
    onHeightResolved: (Int) -> Unit,
    onRenderError: (String?) -> Unit
): WebView {
    return WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {}
        addJavascriptInterface(
            MermaidJsBridge(onHeightResolved, onRenderError),
            "AndroidMermaid"
        )
        loadDataWithBaseURL(
            "file:///android_asset/",
            mermaidHtml(source),
            "text/html",
            "utf-8",
            null
        )
    }
}

private fun mermaidHtml(source: String): String {
    val encodedSource = Base64.encodeToString(source.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
          <script src="mermaid.min.js"></script>
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: transparent;
              color: #111827;
              overflow: hidden;
            }
            #container {
              padding: 8px;
            }
            #container svg {
              width: 100% !important;
              height: auto !important;
            }
            #error {
              display: none;
              padding: 8px;
              color: #b3261e;
              font: 14px sans-serif;
              white-space: pre-wrap;
            }
          </style>
        </head>
        <body>
          <div id="container"></div>
          <div id="error"></div>
          <script>
            const source = atob("$encodedSource");
            mermaid.initialize({ startOnLoad: false, securityLevel: "loose", theme: "default" });
            function reportHeight() {
              const height = Math.ceil(document.documentElement.scrollHeight || document.body.scrollHeight || 320);
              AndroidMermaid.reportHeight(height);
            }
            async function render() {
              try {
                const result = await mermaid.render("remodex-mermaid", source);
                document.getElementById("container").innerHTML = result.svg;
              } catch (error) {
                document.getElementById("error").style.display = "block";
                document.getElementById("error").textContent = "Diagram couldn't be rendered.";
                AndroidMermaid.reportError(String(error));
              }
              requestAnimationFrame(reportHeight);
            }
            render();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private class MermaidJsBridge(
    private val onHeightResolved: (Int) -> Unit,
    private val onRenderError: (String?) -> Unit
) {
    @JavascriptInterface
    fun reportHeight(height: Int) {
        onHeightResolved(height)
    }

    @JavascriptInterface
    fun reportError(message: String?) {
        onRenderError(message)
    }
}
