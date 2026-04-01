package com.remodex.android.ui.turn

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Payload describing an image to preview in the fullscreen viewer.
 * Accepts exactly one of [bitmap], [uri], or [base64].
 */
data class PreviewImagePayload(
    val bitmap: Bitmap? = null,
    val uri: Uri? = null,
    val base64: String? = null,
    val title: String? = null
)

/**
 * Fullscreen zoomable image preview overlay.
 *
 * Mirrors the iOS `ZoomableImagePreviewScreen`:
 * - Pinch-to-zoom (1x..5x) with pan when zoomed
 * - Double-tap toggles between 1x and 2.5x
 * - Close (X) button in the top-end corner
 */
@Composable
fun TurnImagePreview(
    payload: PreviewImagePayload,
    onDismiss: () -> Unit
) {
    val resolvedBitmap = rememberResolvedBitmap(payload)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            if (resolvedBitmap != null) {
                ZoomableImage(
                    bitmap = resolvedBitmap,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Close button
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close preview",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val minScale = 1f
    val maxScale = 5f

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(minScale, maxScale)
        val newOffset = if (newScale > minScale) {
            offset + panChange
        } else {
            Offset.Zero
        }
        scale = newScale
        offset = newOffset
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun rememberResolvedBitmap(payload: PreviewImagePayload): Bitmap? {
    val context = LocalContext.current
    return remember(payload) {
        when {
            payload.bitmap != null -> payload.bitmap
            payload.uri != null -> {
                try {
                    context.contentResolver.openInputStream(payload.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            payload.base64 != null -> {
                try {
                    val raw = payload.base64.let { b64 ->
                        // Strip optional data-URL prefix
                        val commaIdx = b64.indexOf(',')
                        if (commaIdx > 0) b64.substring(commaIdx + 1) else b64
                    }
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
