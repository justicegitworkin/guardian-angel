package com.safeharborsecurity.app.util

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "safeharbor_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SEPARATOR = ":"

        // Part B4: prefix on stored hashes so we can identify the v2 (salted, 10k-round) format.
        private const val PIN_HASH_V2_PREFIX = "v2:"
        private const val PIN_HASH_ROUNDS = 10_000
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getEntry(KEY_ALIAS, null)?.let {
            return (it as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Part B2: Encrypt sensitive text. Never falls back to plaintext — if the
     * AndroidKeyStore entry is corrupt, regenerate it once and retry; if that
     * also fails, throw a SecurityException so callers know the secret cannot
     * be safely persisted.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            doEncrypt(plainText)
        } catch (e: Exception) {
            // Try once more with a fresh key (handles silent keystore corruption / factory reset edge cases)
            try {
                deleteKey()
                doEncrypt(plainText)
            } catch (e2: Exception) {
                throw SecurityException("Cannot encrypt sensitive data. Device keystore may be corrupted.", e2)
            }
        }
    }

    private fun doEncrypt(plainText: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
        )
        return "$iv$IV_SEPARATOR$encrypted"
    }

    private fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {}
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isBlank()) return ""
        if (!encryptedText.contains(IV_SEPARATOR)) return encryptedText // Plain text fallback (legacy migration)
        return try {
            val parts = encryptedText.split(IV_SEPARATOR, limit = 2)
            if (parts.size != 2) return encryptedText

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)

            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // Key was invalidated (factory reset, etc.) — return empty to prompt re-entry
            ""
        }
    }

    /**
     * Part B4: Salted, 10k-round PIN hash. Returns a "v2:" prefixed digest so
     * the verifier can tell new-format hashes from any legacy ones.
     *
     * The salt is the device's ANDROID_ID (or a constant fallback). It isn't
     * secret, but it's unique per device, so a stolen hash table can't be
     * brute-forced offline against many users in one pass.
     */
    fun hashPin(pin: String): String {
        val salt = deviceSalt()
        val saltedPin = "$salt:$pin"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        var hash = digest.digest(saltedPin.toByteArray(Charsets.UTF_8))
        repeat(PIN_HASH_ROUNDS) { hash = digest.digest(hash) }
        return PIN_HASH_V2_PREFIX + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        // v2 format → re-hash the candidate with the same algorithm and compare.
        if (storedHash.startsWith(PIN_HASH_V2_PREFIX)) return hashPin(pin) == storedHash
        // Legacy unsalted hash — verify against the old algorithm so existing testers
        // can still get in once before being prompted to re-set.
        val legacy = Base64.encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(pin.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return legacy == storedHash
    }

    /** True if a stored hash uses the legacy unsalted format and should be upgraded. */
    fun isLegacyPinHash(storedHash: String): Boolean =
        storedHash.isNotBlank() && !storedHash.startsWith(PIN_HASH_V2_PREFIX)

    private fun deviceSalt(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "fallback_salt_safe_companion"
}
