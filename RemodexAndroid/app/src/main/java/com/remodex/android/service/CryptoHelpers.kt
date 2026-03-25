package com.remodex.android.service

import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoHelpers {

    private val secureRandom = SecureRandom()

    // --- Ed25519 ---

    data class Ed25519KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    )

    fun generateEd25519KeyPair(): Ed25519KeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        val pair = generator.generateKeyPair()
        val pubKey = (pair.public as Ed25519PublicKeyParameters).encoded
        val privKey = (pair.private as Ed25519PrivateKeyParameters).encoded
        return Ed25519KeyPair(pubKey, privKey)
    }

    fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) { false }
    }

    // --- X25519 ---

    data class X25519KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    )

    fun generateX25519KeyPair(): X25519KeyPair {
        val privParams = X25519PrivateKeyParameters(secureRandom)
        val pubParams = privParams.generatePublicKey()
        return X25519KeyPair(pubParams.encoded, privParams.encoded)
    }

    fun x25519SharedSecret(myPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(myPrivateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(theirPublicKey, 0), shared, 0)
        return shared
    }

    // --- HKDF-SHA256 ---

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val output = ByteArray(length)
        hkdf.generateBytes(output, 0, length)
        return output
    }

    // --- AES-256-GCM ---

    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): AesGcmResult {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertextWithTag = cipher.doFinal(plaintext)
        // GCM appends 16-byte tag to ciphertext
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)
        return AesGcmResult(ciphertext, tag)
    }

    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        // GCM expects ciphertext + tag concatenated
        val combined = ciphertext + tag
        return cipher.doFinal(combined)
    }

    data class AesGcmResult(val ciphertext: ByteArray, val tag: ByteArray)

    // --- Transcript building ---

    fun buildTranscriptBytes(
        sessionId: String,
        protocolVersion: Int,
        handshakeMode: String,
        keyEpoch: Int,
        macDeviceId: String,
        phoneDeviceId: String,
        macIdentityPublicKey: ByteArray,
        phoneIdentityPublicKey: ByteArray,
        macEphemeralPublicKey: ByteArray,
        phoneEphemeralPublicKey: ByteArray,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        expiresAtForTranscript: String
    ): ByteArray {
        val parts = mutableListOf<ByteArray>()
        parts.add(HANDSHAKE_TAG.toByteArray(Charsets.UTF_8))
        parts.add(sessionId.toByteArray(Charsets.UTF_8))
        parts.add(protocolVersion.toString().toByteArray(Charsets.UTF_8))
        parts.add(handshakeMode.toByteArray(Charsets.UTF_8))
        parts.add(keyEpoch.toString().toByteArray(Charsets.UTF_8))
        parts.add(macDeviceId.toByteArray(Charsets.UTF_8))
        parts.add(phoneDeviceId.toByteArray(Charsets.UTF_8))
        parts.add(macIdentityPublicKey)
        parts.add(phoneIdentityPublicKey)
        parts.add(macEphemeralPublicKey)
        parts.add(phoneEphemeralPublicKey)
        parts.add(clientNonce)
        parts.add(serverNonce)
        parts.add(expiresAtForTranscript.toByteArray(Charsets.UTF_8))

        // Calculate total size
        var totalSize = 0
        for (part in parts) {
            totalSize += 4 + part.size // 4-byte length prefix + payload
        }

        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.BIG_ENDIAN)
        for (part in parts) {
            buffer.putInt(part.size)
            buffer.put(part)
        }
        return buffer.array()
    }

    // --- Nonce construction ---

    fun buildNonce(sender: String, counter: Long): ByteArray {
        val nonce = ByteArray(12)
        nonce[0] = if (sender == "mac") 1 else 2
        // Big-endian counter in bytes 1..11 (we use 8 bytes for the long in positions 4..11)
        val counterBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array()
        // Place counter bytes right-aligned in bytes 4..11
        System.arraycopy(counterBytes, 0, nonce, 4, 8)
        return nonce
    }

    // --- Key derivation from handshake ---

    fun deriveSessionKeys(
        sharedSecret: ByteArray,
        transcriptBytes: ByteArray,
        sessionId: String,
        macDeviceId: String,
        phoneDeviceId: String,
        keyEpoch: Int
    ): Pair<ByteArray, ByteArray> {
        val salt = sha256(transcriptBytes)
        val prefix = "$HANDSHAKE_TAG|$sessionId|$macDeviceId|$phoneDeviceId|$keyEpoch"

        val phoneToMacKey = hkdfSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "$prefix|phoneToMac".toByteArray(Charsets.UTF_8)
        )
        val macToPhoneKey = hkdfSha256(
            ikm = sharedSecret,
            salt = salt,
            info = "$prefix|macToPhone".toByteArray(Charsets.UTF_8)
        )
        return Pair(phoneToMacKey, macToPhoneKey)
    }

    // --- Utility ---

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun toBase64(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    fun fromBase64(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.NO_WRAP)

    // --- Trusted session resolve signature ---

    fun buildTrustedResolveTranscript(
        macDeviceId: String,
        phoneDeviceId: String,
        phoneIdentityPublicKey: ByteArray,
        nonce: ByteArray,
        timestamp: Long
    ): ByteArray {
        val tag = "remodex-trusted-session-resolve-v1".toByteArray(Charsets.UTF_8)
        val macId = macDeviceId.toByteArray(Charsets.UTF_8)
        val phoneId = phoneDeviceId.toByteArray(Charsets.UTF_8)
        val ts = timestamp.toString().toByteArray(Charsets.UTF_8)

        val parts = listOf(tag, macId, phoneId, phoneIdentityPublicKey, nonce, ts)
        var totalSize = 0
        for (part in parts) totalSize += 4 + part.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        for (part in parts) {
            buffer.putInt(part.size)
            buffer.put(part)
        }
        return buffer.array()
    }
}
