package com.remodex.android.data.model

enum class CodexGPTAccountStatus {
    UNKNOWN,
    UNAVAILABLE,
    NOT_LOGGED_IN,
    LOGIN_PENDING,
    AUTHENTICATED,
    EXPIRED;

    val label: String
        get() = when (this) {
            UNKNOWN -> "Unknown"
            UNAVAILABLE -> "Unavailable"
            NOT_LOGGED_IN -> "Not logged in"
            LOGIN_PENDING -> "Login pending"
            AUTHENTICATED -> "Authenticated"
            EXPIRED -> "Expired"
        }
}

data class CodexGPTAccountSnapshot(
    val status: CodexGPTAccountStatus = CodexGPTAccountStatus.UNKNOWN,
    val email: String? = null,
    val planType: String? = null,
    val loginInFlight: Boolean = false,
    val needsReauth: Boolean = false,
    val tokenReady: Boolean? = null,
    val expiresAt: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    val hasActiveLogin: Boolean
        get() = loginInFlight || status == CodexGPTAccountStatus.LOGIN_PENDING

    val isAuthenticated: Boolean
        get() = status == CodexGPTAccountStatus.AUTHENTICATED && !needsReauth

    val canLogout: Boolean
        get() = isAuthenticated || needsReauth

    val isVoiceTokenReady: Boolean
        get() = tokenReady ?: isAuthenticated

    val statusLabel: String
        get() = when {
            status == CodexGPTAccountStatus.AUTHENTICATED && needsReauth -> "Needs reauth"
            else -> status.label
        }

    val detailText: String?
        get() {
            val parts = buildList {
                email?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                planType?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
        }
}
