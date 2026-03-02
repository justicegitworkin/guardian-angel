package com.guardianangel.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import com.guardianangel.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts sensitive values (e.g. the Claude API key) using AES-256-GCM
 * with a key stored in the Android Keystore hardware module.
 *
 * The key never leaves the secure element — only ciphertext is stored in
 * DataStore.  On devices with a StrongBox HSM (Pixel 3+, most 2021+ flagships)
 * the key is backed by dedicated tamper-resistant hardware.
 */
@Singleton
class SecureStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyAlias = "guardian_angel_api_key_v1"
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12  // GCM standard IV length

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(androidKeyStore).also { it.load(null) }
        if (keyStore.containsAlias(keyAlias)) return

        // Try StrongBox first (dedicated secure hardware chip), fall back to TEE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateKey(useStrongBox = true)
                return
            } catch (e: StrongBoxUnavailableException) {
                if (BuildConfig.DEBUG) Log.d(TAG, "StrongBox unavailable, using TEE")
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "StrongBox attempt failed: ${e.message}")
            }
        }
        generateKey(useStrongBox = false)
    }

    private fun generateKey(useStrongBox: Boolean) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, androidKeyStore
        )
        val specBuilder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(true)
        }
        keyGenerator.init(specBuilder.build())
        keyGenerator.generateKey()
    }

    /**
     * Encrypts [plaintext] and returns a Base64-encoded string containing
     * the IV prepended to the ciphertext.  Safe to store in DataStore.
     */
    fun encrypt(plaintext: String): String {
        val key = getKey()
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv                              // 12 bytes
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value previously produced by [encrypt].
     * Returns null if decryption fails (e.g. key was rotated / tampered).
     * The caller should treat null as "key not set" and prompt for re-entry.
     */
    fun decrypt(encoded: String): String? {
        if (encoded.isBlank()) return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= ivLength) return null
            val iv = combined.sliceArray(0 until ivLength)
            val cipherBytes = combined.sliceArray(ivLength until combined.size)
            val key = getKey()
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(gcmTagLength, iv))
            cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).also { it.load(null) }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    companion object {
        private const val TAG = "SecureStorage"
    }
}
