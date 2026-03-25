package com.remodex.android.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CodexImageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val thumbnailBase64JPEG: String? = null,
    val payloadDataURL: String? = null,
    val sourceURL: String? = null
)
