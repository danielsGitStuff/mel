package de.mel.sms.crypto

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SmsCrypto {
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERS = 100_000
    private val DOMAIN_SALT = "mel-sms-v1".toByteArray(StandardCharsets.UTF_8)
    private val rng = SecureRandom()

    fun generateRandomSecret(): String {
        val bytes = ByteArray(32)
        rng.nextBytes(bytes)
        return "b64:" + Base64.getEncoder().encodeToString(bytes)
    }

    fun resolveKey(secret: String): ByteArray {
        require(secret.isNotBlank()) { "Secret must not be blank" }
        if (secret.startsWith("b64:")) {
            val raw = Base64.getDecoder().decode(secret.removePrefix("b64:"))
            require(raw.size == 32) { "Decoded b64 secret must be 32 bytes" }
            return raw
        }
        val spec = PBEKeySpec(secret.toCharArray(), DOMAIN_SALT, PBKDF2_ITERS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Returns Pair(ivBase64, cipherBase64). */
    fun encrypt(key: ByteArray, plaintext: String): Pair<String, String> {
        val iv = ByteArray(IV_BYTES)
        rng.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val out = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val enc = Base64.getEncoder()
        return enc.encodeToString(iv) to enc.encodeToString(out)
    }

    fun decrypt(key: ByteArray, ivB64: String, cipherB64: String): String {
        val dec = Base64.getDecoder()
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, dec.decode(ivB64))
        )
        return String(cipher.doFinal(dec.decode(cipherB64)), StandardCharsets.UTF_8)
    }

    fun hmacHash(key: ByteArray, canonical: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val digest = mac.doFinal(("dedup:$canonical").toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
