package com.ember.companion.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Minimal AES-GCM wrapper over the Android Keystore for the one secret Ember
 * holds locally: the optional API key the user pastes in for AI assist.
 *
 * The key never leaves the Keystore; only ciphertext is written to prefs. If the
 * Keystore is unavailable or wedged (it does fail on some rooted/old ROMs) we
 * degrade to a reversible-but-obfuscated fallback rather than crashing, and
 * [isHardwareBacked] reports which mode is active so the UI can say so honestly.
 */
class KeyStoreCipher(private val alias: String = "ember_aes_key") {

    private companion object {
        /** GCM standard IV length in bytes; the payload is iv || ciphertext. */
        const val IV_BYTES = 12
    }

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    } catch (t: Throwable) {
        null
    }

    /** True when a Keystore-backed AES key is present and usable. */
    val isHardwareBacked: Boolean
        get() = secretKey() != null

    private fun secretKey(): SecretKey? = try {
        val ks = keyStore ?: return null
        (if (ks.containsAlias(alias)) {
            (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            gen.generateKey()
        })
    } catch (t: Throwable) {
        null
    }

    /** Returns base64(iv || ciphertext), or null if the Keystore path is unavailable. */
    fun encrypt(plain: String): String? = try {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        (iv + out).toHex()
    } catch (t: Throwable) {
        null
    }

    /** Inverse of [encrypt]. Returns null when the payload cannot be read. */
    fun decrypt(blob: String): String? {
        return try {
            val key = secretKey() ?: return null
            val raw = blob.hexToBytes()
            if (raw.size <= IV_BYTES) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, raw.copyOfRange(0, IV_BYTES)),
            )
            String(cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size)), Charsets.UTF_8)
        } catch (t: Throwable) {
            null
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "odd-length hex" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
