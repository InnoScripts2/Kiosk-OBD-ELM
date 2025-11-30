package com.selfservice.feature.payments.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedPayload(
    val iv: String,
    val authTag: String,
    val ciphertext: String,
)

private const val KEY_SIZE_BYTES = 32
private const val AUTH_TAG_LENGTH_BYTES = 16
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

class PaymentEncryption(rawKey: String? = null) {
    private val key: ByteArray = coerceKey(
        rawKey
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("PAYMENTS_ENCRYPTION_KEY")
            ?: System.getenv("AGENT_SECRET")
            ?: "selfservice-dev-key",
    )
    private val secureRandom = SecureRandom()

    fun encryptString(value: String?): EncryptedPayload? {
        if (value.isNullOrEmpty()) {
            return null
        }
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val combined = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        require(combined.size >= AUTH_TAG_LENGTH_BYTES) {
            "Encrypted payload is shorter than auth tag"
        }
        val ciphertext = combined.copyOfRange(0, combined.size - AUTH_TAG_LENGTH_BYTES)
        val authTag = combined.copyOfRange(combined.size - AUTH_TAG_LENGTH_BYTES, combined.size)
        return EncryptedPayload(
            iv = Base64.getEncoder().encodeToString(iv),
            authTag = Base64.getEncoder().encodeToString(authTag),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    fun decryptString(payload: EncryptedPayload?): String? {
        if (payload == null) {
            return null
        }
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = Base64.getDecoder().decode(payload.iv)
        val ciphertext = Base64.getDecoder().decode(payload.ciphertext)
        val authTag = Base64.getDecoder().decode(payload.authTag)
        val combined = ciphertext + authTag
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(combined)
        return decrypted.toString(Charsets.UTF_8)
    }

    fun hashString(value: String?): String? {
        if (value.isNullOrEmpty()) {
            return null
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private fun coerceKey(seed: String): ByteArray {
            decodeBase64(seed)?.let { return it }
            decodeHex(seed)?.let { return it }
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(seed.toByteArray(Charsets.UTF_8))
        }

        private fun decodeBase64(input: String): ByteArray? = try {
            val decoded = Base64.getDecoder().decode(input.trim())
            if (decoded.size == KEY_SIZE_BYTES) decoded else null
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun decodeHex(input: String): ByteArray? {
            val sanitized = input.trim()
            if (sanitized.length % 2 != 0) {
                return null
            }
            val bytes = ByteArray(sanitized.length / 2)
            return try {
                for (i in bytes.indices) {
                    val index = i * 2
                    val high = sanitized[index].digitToInt(radix = 16)
                    val low = sanitized[index + 1].digitToInt(radix = 16)
                    bytes[i] = ((high shl 4) + low).toByte()
                }
                if (bytes.size == KEY_SIZE_BYTES) bytes else null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
