package com.remodex.android.ui.turn

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.remodex.android.data.model.CodexImageAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun AttachmentThumbnailStrip(
    attachments: List<CodexImageAttachment>,
    modifier: Modifier = Modifier,
    tileSize: Dp = 84.dp,
    onOpen: ((CodexImageAttachment) -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    overlay: (@Composable BoxScope.(CodexImageAttachment) -> Unit)? = null
) {
    LazyRow(
        modifier = modifier.widthIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            val bitmap = remember(attachment.payloadDataURL, attachment.thumbnailBase64JPEG) {
                decodeAttachmentBitmap(attachment)
            }

            Box(
                modifier = Modifier.size(tileSize),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = onOpen != null) {
                            onOpen?.invoke(attachment)
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                overlay?.let { content ->
                    content(attachment)
                }

                if (onRemove != null) {
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        IconButton(
                            onClick = { onRemove(attachment.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewDialog(
    attachment: CodexImageAttachment,
    onDismiss: () -> Unit
) {
    val bitmap = remember(attachment.payloadDataURL, attachment.thumbnailBase64JPEG) {
        decodeAttachmentBitmap(attachment)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.65f)
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

suspend fun buildCodexImageAttachment(
    context: Context,
    uri: Uri
): CodexImageAttachment? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val imageBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
    if (imageBytes.isEmpty()) return@withContext null

    val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val payloadDataUrl = "data:$mimeType;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    val thumbnail = buildThumbnailBase64(imageBytes)

    CodexImageAttachment(
        thumbnailBase64JPEG = thumbnail,
        payloadDataURL = payloadDataUrl,
        sourceURL = uri.toString()
    )
}

fun createCameraCaptureUri(context: Context): Uri? {
    return try {
        val directory = File(context.cacheDir, "camera-captures").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val imageFile = File.createTempFile("remodex-camera-", ".jpg", directory)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    } catch (_: Exception) {
        null
    }
}

fun deleteCapturedImage(context: Context, uri: Uri?) {
    if (uri == null) return
    runCatching {
        context.contentResolver.delete(uri, null, null)
    }.onFailure {
        runCatching {
            val directory = File(context.cacheDir, "camera-captures")
            File(directory, uri.lastPathSegment.orEmpty()).delete()
        }
    }
}

private fun decodeAttachmentBitmap(attachment: CodexImageAttachment): ImageBitmap? {
    val payloadData = attachment.payloadDataURL?.let(::decodeImageDataFromDataUrl)
    val imageBytes = payloadData ?: attachment.thumbnailBase64JPEG?.let {
        Base64.decode(it, Base64.DEFAULT)
    }
    val bitmap = imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return null
    return bitmap.asImageBitmap()
}

private fun decodeImageDataFromDataUrl(dataUrl: String): ByteArray? {
    val commaIndex = dataUrl.indexOf(',')
    if (commaIndex <= 0) return null

    val metadata = dataUrl.substring(0, commaIndex).lowercase()
    if (!metadata.startsWith("data:image") || !metadata.contains(";base64")) return null

    return Base64.decode(dataUrl.substring(commaIndex + 1), Base64.DEFAULT)
}

private fun buildThumbnailBase64(imageBytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

    var sampleSize = 1
    while ((bounds.outWidth / sampleSize) > 512 || (bounds.outHeight / sampleSize) > 512) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize.coerceAtLeast(1) }
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)
    bitmap.recycle()
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
