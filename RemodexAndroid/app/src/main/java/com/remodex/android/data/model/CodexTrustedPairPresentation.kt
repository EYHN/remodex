package com.remodex.android.data.model

data class CodexTrustedPairPresentation(
    val deviceId: String?,
    val title: String,
    val name: String,
    val systemName: String?,
    val detail: String?,
    val nickname: String = ""
)
