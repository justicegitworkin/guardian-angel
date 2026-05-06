package com.safeharborsecurity.app.util

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for KeystoreManager's PIN hashing and verification.
 *
 * We can't test the AndroidKeyStore-backed encrypt/decrypt path on the JVM
 * (Robolectric doesn't simulate hardware-backed keys), but the PIN hash
 * functions only use SHA-256 + ANDROID_ID, which Robolectric DOES provide.
 *
 * Bug history these tests guard against:
 *  - Fix 43: KeystoreManager plaintext fallback vulnerability
 *  - The recent PIN re-prompt bug — though that was in MainActivity, not
 *    here, having tests on PIN verify means we can refactor with confidence
 *    if that area gets touched again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])  // Pin to API 33 — its Robolectric SDK jar is widely cached
                     // so we don't get a network fetch every CI run.
class KeystoreManagerPinTest {

    private lateinit var keystoreManager: KeystoreManager

    @Before
    fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        keystoreManager = KeystoreManager(context)
    }

    @Test
    fun `correct PIN verifies`() {
        val hash = keystoreManager.hashPin("1234")
        assertThat(keystoreManager.verifyPin("1234", hash)).isTrue()
    }

    @Test
    fun `wrong PIN fails verification`() {
        val hash = keystoreManager.hashPin("1234")
        assertThat(keystoreManager.verifyPin("0000", hash)).isFalse()
    }

    @Test
    fun `blank stored hash always fails`() {
        // Critical: an empty hash field must never authenticate any PIN.
        // Otherwise an attacker who clears app data could log in with empty PIN.
        assertThat(keystoreManager.verifyPin("1234", "")).isFalse()
        assertThat(keystoreManager.verifyPin("", "")).isFalse()
        assertThat(keystoreManager.verifyPin("anything", "")).isFalse()
    }

    @Test
    fun `same PIN produces same hash deterministically`() {
        // Required for verifyPin to work — must be a function, not a stream.
        val hash1 = keystoreManager.hashPin("1234")
        val hash2 = keystoreManager.hashPin("1234")
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `different PINs produce different hashes`() {
        val hash1 = keystoreManager.hashPin("1234")
        val hash2 = keystoreManager.hashPin("4321")
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `v2 hash has v2 prefix`() {
        // The prefix is what the verifier uses to distinguish modern from
        // legacy hashes. If this regresses, isLegacyPinHash will misfire.
        val hash = keystoreManager.hashPin("9876")
        assertThat(hash).startsWith("v2:")
    }

    @Test
    fun `isLegacyPinHash returns false for new hashes`() {
        val hash = keystoreManager.hashPin("1234")
        assertThat(keystoreManager.isLegacyPinHash(hash)).isFalse()
    }

    @Test
    fun `isLegacyPinHash returns true for old format`() {
        // Pretend a tester has an old-format hash from a pre-Fix-43 install.
        val legacyHash = "abcdef1234567890=="  // no v2: prefix
        assertThat(keystoreManager.isLegacyPinHash(legacyHash)).isTrue()
    }

    @Test
    fun `isLegacyPinHash returns false for blank`() {
        assertThat(keystoreManager.isLegacyPinHash("")).isFalse()
    }

    @Test
    fun `long PIN works`() {
        // Don't artificially restrict. Test 16-char PIN.
        val pin = "1234567890ABCDEF"
        val hash = keystoreManager.hashPin(pin)
        assertThat(keystoreManager.verifyPin(pin, hash)).isTrue()
    }

    @Test
    fun `unicode PIN works`() {
        // Defensive — the hashing is byte-based, so unicode shouldn't break.
        val pin = "😀1234"
        val hash = keystoreManager.hashPin(pin)
        assertThat(keystoreManager.verifyPin(pin, hash)).isTrue()
    }

    @Test
    fun `case sensitivity is preserved`() {
        val hashLower = keystoreManager.hashPin("abcd")
        val hashUpper = keystoreManager.hashPin("ABCD")
        assertThat(hashLower).isNotEqualTo(hashUpper)
        assertThat(keystoreManager.verifyPin("abcd", hashLower)).isTrue()
        assertThat(keystoreManager.verifyPin("ABCD", hashLower)).isFalse()
    }

    @Test
    fun `hash is reasonably long for SHA-256`() {
        // Sanity check: base64 of 32 bytes is 44 chars; with v2: prefix that's 47.
        val hash = keystoreManager.hashPin("1234")
        assertThat(hash.length).isAtLeast(40)
    }
}
