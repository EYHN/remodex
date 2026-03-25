package com.remodex.android.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SECURE_PROTOCOL_VERSION = 1
const val PAIRING_QR_VERSION = 2
const val HANDSHAKE_TAG = "remodex-e2ee-v1"
const val MAX_PAIRING_AGE_MS = 5 * 60 * 1000L

@Serializable
data class CodexPairingQRPayload(
    val v: Int,
    val relay: String,
    val sessionId: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val expiresAt: String? = null
) {
    val isExpired: Boolean get() {
        val exp = expiresAt ?: return false
        return try {
            val ts = java.time.Instant.parse(exp).toEpochMilli()
            System.currentTimeMillis() > ts
        } catch (_: Exception) { false }
    }

    val isValid: Boolean get() = v == PAIRING_QR_VERSION
            && relay.isNotBlank()
            && sessionId.isNotBlank()
            && macDeviceId.isNotBlank()
            && macIdentityPublicKey.isNotBlank()
            && !isExpired
}

@Serializable
data class CodexPhoneIdentityState(
    val phoneDeviceId: String,
    val identityPublicKey: String,     // Base64 Ed25519 public key
    val identityPrivateKey: String     // Base64 Ed25519 private key
)

@Serializable
data class CodexTrustedMacRecord(
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val displayName: String? = null,
    val lastConnectedAt: Long = 0
)

@Serializable
data class CodexTrustedMacRegistry(
    val macs: MutableMap<String, CodexTrustedMacRecord> = mutableMapOf()
)

// Handshake messages
@Serializable
data class SecureClientHello(
    val kind: String = "clientHello",
    val protocolVersion: Int = SECURE_PROTOCOL_VERSION,
    val sessionId: String,
    val handshakeMode: String,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: String,
    val phoneEphemeralPublicKey: String,
    val clientNonce: String
)

@Serializable
data class SecureServerHello(
    val kind: String = "serverHello",
    val protocolVersion: Int = SECURE_PROTOCOL_VERSION,
    val sessionId: String,
    val handshakeMode: String,
    val macDeviceId: String,
    val macIdentityPublicKey: String,
    val macEphemeralPublicKey: String,
    val serverNonce: String,
    val keyEpoch: Int,
    val expiresAtForTranscript: String? = null,
    val macSignature: String,
    val clientNonce: String? = null
)

@Serializable
data class SecureClientAuth(
    val kind: String = "clientAuth",
    val sessionId: String,
    val phoneDeviceId: String,
    val keyEpoch: Int,
    val phoneSignature: String
)

@Serializable
data class SecureReadyMessage(
    val kind: String = "secureReady",
    val sessionId: String? = null,
    val keyEpoch: Int? = null
)

@Serializable
data class SecureResumeState(
    val kind: String = "resumeState",
    val sessionId: String,
    val keyEpoch: Int,
    val lastAppliedBridgeOutboundSeq: Int? = null
)

@Serializable
data class SecureErrorMessage(
    val kind: String = "secureError",
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class SecureEnvelope(
    val kind: String = "encryptedEnvelope",
    val v: Int = 1,
    val sessionId: String,
    val keyEpoch: Int,
    val sender: String,
    val counter: Long,
    val ciphertext: String,
    val tag: String
)

@Serializable
data class SecureApplicationPayload(
    @SerialName("payloadText") val payloadText: String
)

// Session state during active encrypted connection
data class CodexSecureSession(
    val sessionId: String,
    val keyEpoch: Int,
    val phoneToMacKey: ByteArray,
    val macToPhoneKey: ByteArray,
    var phoneCounter: Long = 0,
    var lastMacCounter: Long = -1
) {
    fun nextPhoneCounter(): Long = phoneCounter++
}

data class CodexPendingHandshake(
    val sessionId: String,
    val handshakeMode: String,
    val clientNonce: ByteArray,
    val phoneEphemeralPrivateKey: ByteArray,
    val phoneEphemeralPublicKey: ByteArray,
    val phoneDeviceId: String,
    val phoneIdentityPublicKey: ByteArray,
    val phoneIdentityPrivateKey: ByteArray
)

enum class CodexSecureConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED_ENCRYPTED,
    ERROR
}

enum class CodexConnectionPhase {
    OFFLINE,
    CONNECTING,
    HANDSHAKING,
    LOADING_CHATS,
    SYNCING,
    CONNECTED
}
