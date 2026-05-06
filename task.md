# Safe Companion — Security Hardening & Test-Ready Distribution

## Autonomous Operation Mode

Work fully autonomously. Do NOT prompt the user for input unless:
- A build fails and you cannot determine the fix after 3 attempts
- You need a credential, API key, or secret that isn't in the codebase

For everything else — make the best decision and keep going.

---

## Overview

This task hardens the Safe Companion app against reverse engineering, reduces
cloud API dependency by using an on-device Small Language Model (SLM), fixes
all security vulnerabilities found in audit, and packages the app so testers
need ZERO API key configuration to get started.

Execute Parts A through E in order. Run `./gradlew assembleDebug` after each
part. Fix all errors before continuing.

---

## Part A — On-Device Small Language Model (Replace Claude Haiku Where Possible)

### A1. Add ONNX Runtime Dependency

The goal: run a small language model ON THE DEVICE for common scam detection
tasks instead of calling Claude Haiku over the network. This means:
- No API key needed for basic scam detection
- Works fully offline
- Faster response for simple cases
- Claude Haiku becomes a FALLBACK for complex/ambiguous cases only

Add to `app/build.gradle.kts`:
```kotlin
// On-device ML inference
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
```

### A2. Create On-Device Scam Classifier

Create `app/src/main/java/com/safeharborsecurity/app/ml/OnDeviceScamClassifier.kt`

This class uses a rule-based + TF-IDF style approach (no large model file needed).
It handles the 80% of scam detection cases that are obvious pattern matches,
reserving Claude Haiku for the 20% that are ambiguous.

```kotlin
package com.safeharborsecurity.app.ml

import javax.inject.Inject
import javax.inject.Singleton

data class ScamClassification(
    val verdict: String,        // "DANGEROUS", "SUSPICIOUS", "SAFE", "UNKNOWN"
    val confidence: Float,      // 0.0 to 1.0
    val reasons: List<String>,
    val needsCloudVerification: Boolean  // true = send to Claude for deeper analysis
)

@Singleton
class OnDeviceScamClassifier @Inject constructor() {

    // ── Scam Pattern Database (updatable via WorkManager sync) ──

    private val urgencyPhrases = listOf(
        "act now", "immediate action", "urgent", "expires today",
        "last chance", "limited time", "don't delay", "right away",
        "within 24 hours", "within 48 hours", "account suspended",
        "account will be closed", "verify immediately", "confirm now",
        "failure to respond", "your account has been compromised"
    )

    private val financialScamPhrases = listOf(
        "wire transfer", "gift card", "itunes card", "google play card",
        "send money", "western union", "moneygram", "bitcoin",
        "cryptocurrency", "investment opportunity", "guaranteed return",
        "double your money", "nigerian prince", "lottery winner",
        "you've won", "claim your prize", "inheritance",
        "irs", "tax refund", "social security", "medicare",
        "bank account", "routing number", "account number",
        "credit card number", "ssn", "social security number"
    )

    private val impersonationPhrases = listOf(
        "this is the irs", "this is the fbi", "microsoft support",
        "apple support", "amazon security", "bank of america fraud",
        "wells fargo alert", "chase security", "paypal security",
        "geek squad", "norton security", "mcafee alert",
        "your computer has a virus", "we detected suspicious activity",
        "your package could not be delivered", "usps", "fedex delivery",
        "customs and border"
    )

    private val phishingUrlPatterns = listOf(
        Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""),    // IP address URLs
        Regex("""https?://[^/]*\.tk(/|$)"""),                          // Free TLD .tk
        Regex("""https?://[^/]*\.ml(/|$)"""),                          // Free TLD .ml
        Regex("""https?://[^/]*\.ga(/|$)"""),                          // Free TLD .ga
        Regex("""https?://[^/]*\.cf(/|$)"""),                          // Free TLD .cf
        Regex("""https?://bit\.ly/"""),                                 // URL shorteners
        Regex("""https?://tinyurl\.com/"""),
        Regex("""https?://t\.co/"""),
        Regex("""https?://goo\.gl/"""),
        Regex("""https?://[^/]*-login[^/]*\."""),                      // Fake login pages
        Regex("""https?://[^/]*verify[^/]*-account"""),
        Regex("""https?://[^/]*secure[^/]*-update"""),
        Regex("""http://"""),                                           // Non-HTTPS
    )

    private val safePatterns = listOf(
        "your order has shipped", "delivery confirmed",
        "appointment reminder", "your prescription is ready",
        "weather alert", "school closing", "meeting reminder"
    )

    // ── Known Scam Phone Number Prefixes ──

    private val scamNumberPatterns = listOf(
        Regex("""^\+1900"""),           // Premium rate
        Regex("""^\+1976"""),           // Premium rate UK
        Regex("""^\+?(232|234|242|246|284|340|441|473|649|664|721|758|767|784|809|829|849|868|869|876)\d+"""),  // Known high-fraud country codes
    )

    // ── Main Classification Method ──

    fun classifyText(text: String, sender: String? = null): ScamClassification {
        val lowerText = text.lowercase()
        val reasons = mutableListOf<String>()
        var dangerScore = 0f
        var safeScore = 0f

        // Check safe patterns first
        for (pattern in safePatterns) {
            if (lowerText.contains(pattern)) safeScore += 0.3f
        }

        // Urgency scoring
        val urgencyHits = urgencyPhrases.count { lowerText.contains(it) }
        if (urgencyHits >= 2) {
            dangerScore += 0.3f
            reasons.add("Uses urgent pressure tactics ($urgencyHits phrases)")
        } else if (urgencyHits == 1) {
            dangerScore += 0.1f
        }

        // Financial scam scoring
        val financialHits = financialScamPhrases.count { lowerText.contains(it) }
        if (financialHits >= 2) {
            dangerScore += 0.4f
            reasons.add("Contains financial scam language ($financialHits matches)")
        } else if (financialHits == 1) {
            dangerScore += 0.15f
            reasons.add("Mentions financial terms that scammers commonly use")
        }

        // Impersonation scoring
        val impersonationHits = impersonationPhrases.count { lowerText.contains(it) }
        if (impersonationHits >= 1) {
            dangerScore += 0.35f
            reasons.add("Appears to impersonate a known organization")
        }

        // URL analysis
        val urls = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""").findAll(text)
        for (url in urls) {
            val urlStr = url.value
            for (pattern in phishingUrlPatterns) {
                if (pattern.containsMatchIn(urlStr)) {
                    dangerScore += 0.25f
                    reasons.add("Contains suspicious URL: ${urlStr.take(50)}...")
                    break
                }
            }
        }

        // Phone number analysis
        if (sender != null) {
            for (pattern in scamNumberPatterns) {
                if (pattern.containsMatchIn(sender.replace("[^+\\d]".toRegex(), ""))) {
                    dangerScore += 0.3f
                    reasons.add("Sender number matches known scam patterns")
                    break
                }
            }
        }

        // Grammar/spelling heuristics (common in scam messages)
        val grammarIssues = listOf(
            "kindly", "do the needful", "dear customer", "dear user",
            "dear friend", "valued customer", "esteemed", "revert back"
        )
        val grammarHits = grammarIssues.count { lowerText.contains(it) }
        if (grammarHits >= 2) {
            dangerScore += 0.15f
            reasons.add("Language patterns common in scam messages")
        }

        // ALL-CAPS detection (urgency tactic)
        val capsRatio = text.count { it.isUpperCase() }.toFloat() / text.length.coerceAtLeast(1)
        if (capsRatio > 0.4f && text.length > 20) {
            dangerScore += 0.1f
            reasons.add("Excessive use of capital letters (urgency tactic)")
        }

        // ── Determine Verdict ──
        val confidence: Float
        val verdict: String
        val needsCloud: Boolean

        when {
            dangerScore >= 0.6f -> {
                verdict = "DANGEROUS"
                confidence = (dangerScore).coerceAtMost(0.95f)
                needsCloud = false  // High confidence locally
            }
            dangerScore >= 0.35f -> {
                verdict = "SUSPICIOUS"
                confidence = dangerScore
                needsCloud = dangerScore < 0.5f  // Medium confidence → verify with Claude
            }
            safeScore >= 0.3f && dangerScore < 0.15f -> {
                verdict = "SAFE"
                confidence = (0.7f + safeScore).coerceAtMost(0.9f)
                needsCloud = false
            }
            dangerScore < 0.1f -> {
                verdict = "SAFE"
                confidence = 0.6f
                needsCloud = false
            }
            else -> {
                verdict = "UNKNOWN"
                confidence = 0.3f
                needsCloud = true  // Ambiguous → ask Claude
            }
        }

        if (reasons.isEmpty() && verdict == "SAFE") {
            reasons.add("No scam indicators detected")
        }

        return ScamClassification(
            verdict = verdict,
            confidence = confidence,
            reasons = reasons,
            needsCloudVerification = needsCloud
        )
    }

    fun classifyUrl(url: String): ScamClassification {
        val reasons = mutableListOf<String>()
        var dangerScore = 0f

        // Check URL patterns
        for (pattern in phishingUrlPatterns) {
            if (pattern.containsMatchIn(url)) {
                dangerScore += 0.3f
                reasons.add("URL matches known phishing pattern")
            }
        }

        // Non-HTTPS
        if (url.startsWith("http://") && !url.startsWith("http://localhost")) {
            dangerScore += 0.2f
            reasons.add("Uses unencrypted HTTP connection")
        }

        // Very long URLs (common in phishing)
        if (url.length > 200) {
            dangerScore += 0.1f
            reasons.add("Unusually long URL (common in phishing)")
        }

        // Homograph attack detection (mixed scripts)
        val hasNonAscii = url.any { it.code > 127 }
        if (hasNonAscii) {
            dangerScore += 0.3f
            reasons.add("URL contains non-standard characters (possible homograph attack)")
        }

        val verdict = when {
            dangerScore >= 0.5f -> "DANGEROUS"
            dangerScore >= 0.2f -> "SUSPICIOUS"
            else -> "SAFE"
        }

        return ScamClassification(
            verdict = verdict,
            confidence = dangerScore.coerceIn(0.3f, 0.9f),
            reasons = reasons.ifEmpty { listOf("No known threats detected in URL") },
            needsCloudVerification = verdict == "SUSPICIOUS"
        )
    }

    fun classifyPhoneNumber(number: String): ScamClassification {
        val cleaned = number.replace("[^+\\d]".toRegex(), "")
        val reasons = mutableListOf<String>()
        var dangerScore = 0f

        for (pattern in scamNumberPatterns) {
            if (pattern.containsMatchIn(cleaned)) {
                dangerScore += 0.5f
                reasons.add("Number matches known scam/premium-rate pattern")
            }
        }

        // Very short caller ID (spoofed)
        if (cleaned.length in 1..5) {
            dangerScore += 0.2f
            reasons.add("Very short number (possibly spoofed)")
        }

        val verdict = when {
            dangerScore >= 0.5f -> "DANGEROUS"
            dangerScore >= 0.2f -> "SUSPICIOUS"
            else -> "UNKNOWN"
        }

        return ScamClassification(
            verdict = verdict,
            confidence = dangerScore.coerceIn(0.2f, 0.85f),
            reasons = reasons.ifEmpty { listOf("Number not in known scam databases") },
            needsCloudVerification = true // Always verify phone numbers with Claude
        )
    }
}
```

### A3. Create Hybrid Analysis Repository

Create `app/src/main/java/com/safeharborsecurity/app/data/repository/HybridAnalysisRepository.kt`

This is the central "brain" — tries local SLM first, falls back to Claude Haiku
only when confidence is low or the case is ambiguous.

```kotlin
package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.ml.OnDeviceScamClassifier
import com.safeharborsecurity.app.ml.ScamClassification
import javax.inject.Inject
import javax.inject.Singleton

data class AnalysisResult(
    val verdict: String,
    val confidence: Float,
    val reasons: List<String>,
    val analysisSource: String  // "on_device" or "cloud_ai"
)

@Singleton
class HybridAnalysisRepository @Inject constructor(
    private val localClassifier: OnDeviceScamClassifier,
    private val chatRepository: ChatRepository,  // Existing Claude API repo
    private val userPreferences: com.safeharborsecurity.app.data.datastore.UserPreferences
) {
    /**
     * Analyze text for scam indicators.
     * Uses on-device classifier first. Only calls Claude if:
     * 1. Local confidence is low (needs verification)
     * 2. User has a valid API key configured
     * 3. Device has network connectivity
     */
    suspend fun analyzeText(
        text: String,
        sender: String? = null,
        forceCloud: Boolean = false
    ): AnalysisResult {
        // Step 1: Local classification (instant, free, offline)
        val localResult = localClassifier.classifyText(text, sender)

        // Step 2: If local is confident enough, return immediately
        if (!forceCloud && !localResult.needsCloudVerification) {
            return AnalysisResult(
                verdict = localResult.verdict,
                confidence = localResult.confidence,
                reasons = localResult.reasons,
                analysisSource = "on_device"
            )
        }

        // Step 3: Try cloud verification (Claude Haiku) if available
        val apiKey = userPreferences.claudeApiKeyFlow.first()
        if (apiKey.isNullOrBlank()) {
            // No API key — return local result with lower confidence
            return AnalysisResult(
                verdict = localResult.verdict,
                confidence = localResult.confidence * 0.8f,
                reasons = localResult.reasons + "Cloud verification unavailable (no API key)",
                analysisSource = "on_device"
            )
        }

        return try {
            val claudeResult = chatRepository.analyzeForScam(apiKey, text, sender)
            AnalysisResult(
                verdict = claudeResult.verdict,
                confidence = claudeResult.confidence,
                reasons = claudeResult.reasons,
                analysisSource = "cloud_ai"
            )
        } catch (e: Exception) {
            // Cloud failed — return local result
            AnalysisResult(
                verdict = localResult.verdict,
                confidence = localResult.confidence * 0.85f,
                reasons = localResult.reasons + "Cloud verification failed: ${e.message}",
                analysisSource = "on_device"
            )
        }
    }

    suspend fun analyzeUrl(url: String): AnalysisResult {
        val localResult = localClassifier.classifyUrl(url)
        if (!localResult.needsCloudVerification) {
            return AnalysisResult(
                verdict = localResult.verdict,
                confidence = localResult.confidence,
                reasons = localResult.reasons,
                analysisSource = "on_device"
            )
        }
        // ... same cloud fallback pattern as analyzeText
        return AnalysisResult(
            verdict = localResult.verdict,
            confidence = localResult.confidence,
            reasons = localResult.reasons,
            analysisSource = "on_device"
        )
    }
}
```

### A4. Wire HybridAnalysisRepository Into Existing Code

Update these files to use `HybridAnalysisRepository` instead of calling Claude directly:

1. **`SafeHarborNotificationListener.kt`** — SMS/notification scanning should call
   `hybridAnalysisRepository.analyzeText()` instead of Claude API directly
2. **`SafetyCheckerRepository.kt`** — URL/text/image checking should try local first
3. **`SafeHarborCallScreeningService.kt`** — phone number checking should use
   `localClassifier.classifyPhoneNumber()` (instant, no network needed for calls)

**Important:** The chat agent (Grace/James/Sophie/George) should STILL use Claude
Haiku for conversation — the SLM is for classification only, not conversation.

### A5. Add Scam Pattern Database Auto-Update

Create `app/src/main/java/com/safeharborsecurity/app/service/ScamPatternSyncWorker.kt`

WorkManager task that runs weekly to fetch updated scam patterns from a simple
JSON endpoint (or bundled asset update). For now, bundle patterns in the APK.
The architecture supports remote updates later.

```kotlin
// For now, patterns are compiled into the app.
// Future: fetch from https://safecompanion-api.azurewebsites.net/api/v1/patterns
// and merge into local database, allowing pattern updates without app updates.
```

### A6. Provide OnDeviceScamClassifier via Hilt

In `di/AppModule.kt` or create `di/MlModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MlModule {
    @Provides
    @Singleton
    fun provideOnDeviceScamClassifier(): OnDeviceScamClassifier {
        return OnDeviceScamClassifier()
    }
}
```

---

## Part B — Security Hardening (Audit Fixes)

### B1. Fix Debug Logging Exposure

**Problem:** 165 Log statements across 32 files. API key prefixes logged in
ElevenLabsTTSManager and SettingsViewModel. HttpLoggingInterceptor logs full
request/response bodies including API keys.

**Fix:** Create a secure logging wrapper.

Create `app/src/main/java/com/safeharborsecurity/app/util/SecureLog.kt`:

```kotlin
package com.safeharborsecurity.app.util

import android.util.Log
import com.safeharborsecurity.app.BuildConfig

/**
 * Secure logging wrapper. In release builds, all logging is suppressed.
 * In debug builds, sensitive data is redacted.
 */
object SecureLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, redact(message))
    }
    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, redact(message))
    }
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, redact(message), throwable)
    }
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, redact(message))
    }

    private fun redact(message: String): String {
        return message
            // Redact anything that looks like an API key (sk-, key-, etc.)
            .replace(Regex("""(sk-[a-zA-Z0-9]{4})[a-zA-Z0-9]+"""), "$1****")
            .replace(Regex("""(key[_-]?)[a-zA-Z0-9]{8,}""", RegexOption.IGNORE_CASE), "$1****")
            // Redact email addresses
            .replace(Regex("""[\w.]+@[\w.]+\.\w+"""), "***@***.***")
    }
}
```

**Then:**
1. Replace ALL `Log.d/w/e/i` calls with `SecureLog.d/w/e/i` across all 32 files
2. Specifically remove the `EL_DEBUG` API key prefix logging in ElevenLabsTTSManager.kt
   and SettingsViewModel.kt
3. Fix `AppModule.kt` OkHttp logging:

```kotlin
fun provideOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS  // NEVER log BODY
                })
            }
        }
        .build()
}
```

### B2. Fix KeystoreManager Plaintext Fallback

**Problem:** `encrypt()` returns plaintext on exception (line 52). This silently
degrades security without the user knowing.

**Fix:** Change the catch block to throw or return a sentinel that forces re-entry:

```kotlin
fun encrypt(plainText: String): String {
    if (plainText.isBlank()) return ""
    return try {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(
            cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
        )
        "$iv$IV_SEPARATOR$encrypted"
    } catch (e: Exception) {
        // Do NOT fall back to plaintext. Regenerate key and retry once.
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(
                cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP
            )
            "$iv$IV_SEPARATOR$encrypted"
        } catch (e2: Exception) {
            throw SecurityException("Cannot encrypt sensitive data. Device keystore may be corrupted.", e2)
        }
    }
}
```

### B3. Add Certificate Pinning

Update `network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.anthropic.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Pin the intermediate CA, not the leaf cert (more stable) -->
            <!-- Amazon Root CA 1 (Anthropic uses AWS) -->
            <pin digest="SHA-256">++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI=</pin>
            <!-- Backup: Amazon Root CA 2 -->
            <pin digest="SHA-256">f0KW/FtqTjs108NpYj42SrGvOB2PpxIVM8nWxjPqJGE=</pin>
        </pin-set>
    </domain-config>

    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.elevenlabs.io</domain>
        <domain includeSubdomains="true">elevenlabs.io</domain>
        <pin-set expiration="2027-01-01">
            <!-- Cloudflare pins (ElevenLabs uses Cloudflare) -->
            <pin digest="SHA-256">jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=</pin>
            <!-- Backup pin -->
            <pin digest="SHA-256">C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=</pin>
        </pin-set>
    </domain-config>

    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">texttospeech.googleapis.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Google Trust Services Root -->
            <pin digest="SHA-256">hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=</pin>
            <!-- GTS Root R1 backup -->
            <pin digest="SHA-256">zCTnfLwLKbS9S2sbp+uFz4KZOocFvXxkV06Ce9O5M2w=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

**IMPORTANT NOTE TO IMPLEMENTER:** The SHA-256 pin hashes above are EXAMPLES.
Before deploying, you MUST extract the actual certificate pins from each domain.
Run this for each domain to get the correct pins:
```bash
openssl s_client -connect api.anthropic.com:443 -servername api.anthropic.com 2>/dev/null | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform DER | \
  openssl dgst -sha256 -binary | base64
```

If you cannot extract pins at build time, use the trust-anchors approach WITHOUT
pin-set but WITH the base-config cleartext=false. Certificate pinning can be
added in a follow-up once actual pin hashes are verified.

**Fallback if pinning causes issues:** Keep the domain-config blocks but remove
the pin-set elements. The cleartext=false enforcement is the minimum required.

### B4. Add PIN Hashing Salt

**Problem:** `hashPin()` uses SHA-256 without salt. Vulnerable to rainbow tables.

**Fix:** Add per-device salt:

```kotlin
fun hashPin(pin: String): String {
    // Use Android ID as device-specific salt (not secret, but unique per device)
    val salt = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: "fallback_salt_safe_companion"
    val saltedPin = "$salt:$pin"
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    // Multiple rounds to slow brute force
    var hash = digest.digest(saltedPin.toByteArray(Charsets.UTF_8))
    repeat(10_000) { hash = digest.digest(hash) }
    return Base64.encodeToString(hash, Base64.NO_WRAP)
}
```

**Note:** This changes the hash format, so existing PINs will need to be re-set.
Add a migration: if the stored hash doesn't match the new format, prompt the
user to re-enter their PIN on next app open.

### B5. Complete ProGuard Rules

Replace `app/proguard-rules.pro` with comprehensive rules:

```proguard
# ═══════════════════════════════════════════════════
# Safe Companion — R8/ProGuard Rules
# ═══════════════════════════════════════════════════

# ── Aggressive obfuscation ──
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification
-dontpreverify

# ── Keep Room entities (reflection) ──
-keep class com.safeharborsecurity.app.data.remote.model.** { *; }
-keep class com.safeharborsecurity.app.data.local.entity.** { *; }
-keep class com.safeharborsecurity.app.data.model.** { *; }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }

# ── Retrofit / OkHttp ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.safeharborsecurity.app.data.remote.ClaudeApiService { *; }

# ── Gson serialization ──
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Hilt / Dagger ──
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel

# ── Kotlin ──
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ── ML Kit ──
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Compose (keep preview functions for debug, strip in release) ──
-dontwarn androidx.compose.**

# ── ONNX Runtime ──
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── Remove all Log calls in release ──
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

# ── Remove SecureLog calls in release too ──
-assumenosideeffects class com.safeharborsecurity.app.util.SecureLog {
    public static void d(...);
    public static void w(...);
    public static void i(...);
}

# ── Encrypt string constants (R8 full mode) ──
# R8 full mode (default in AGP 8+) already does this, but ensure it's on:
-android

# ── Remove debugging info ──
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
```

---

## Part C — Anti-Reverse-Engineering Hardening

### C1. Add Runtime Integrity Checks

Create `app/src/main/java/com/safeharborsecurity/app/security/IntegrityChecker.kt`:

```kotlin
package com.safeharborsecurity.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Runtime checks to detect tampering, rooting, emulators, and debugging.
 * Each check returns a risk level. The app degrades gracefully rather than
 * crashing (so legitimate users on rooted devices still get SOME protection).
 */
object IntegrityChecker {

    data class IntegrityReport(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val isDebuggable: Boolean,
        val isDebuggerAttached: Boolean,
        val isTampered: Boolean,
        val riskLevel: RiskLevel  // LOW, MEDIUM, HIGH
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    fun check(context: Context): IntegrityReport {
        val rooted = checkRoot()
        val emulator = checkEmulator()
        val debuggable = checkDebuggable(context)
        val debuggerAttached = android.os.Debug.isDebuggerConnected()
        val tampered = checkTampering(context)

        val riskScore = listOf(rooted, emulator, debuggable, debuggerAttached, tampered)
            .count { it }

        val riskLevel = when {
            riskScore >= 3 -> RiskLevel.HIGH
            riskScore >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return IntegrityReport(
            isRooted = rooted,
            isEmulator = emulator,
            isDebuggable = debuggable,
            isDebuggerAttached = debuggerAttached,
            isTampered = tampered,
            riskLevel = riskLevel
        )
    }

    private fun checkRoot(): Boolean {
        // Check common root paths
        val rootPaths = listOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su", "/system/app/SuperSU.apk", "/system/app/SuperSU",
            "/system/app/Magisk.apk", "/sbin/magisk"
        )
        if (rootPaths.any { File(it).exists() }) return true

        // Check if su is executable
        return try {
            val process = Runtime.getRuntime().exec("which su")
            process.inputStream.bufferedReader().readLine() != null
        } catch (e: Exception) {
            false
        }
    }

    private fun checkEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD == "QC_Reference_Phone"
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic")
                || Build.PRODUCT.contains("sdk_gphone")
                || Build.PRODUCT.contains("vbox86p")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Settings.Secure.getString(null, "android_id") == null)
    }

    private fun checkDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun checkTampering(context: Context): Boolean {
        return try {
            // Verify the app was installed from an expected source
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            // Allow: Play Store, Samsung Store, Amazon, null (ADB sideload for testing),
            //         package installer (APK install)
            val allowedInstallers = setOf(
                "com.android.vending",
                "com.sec.android.app.samsungapps",
                "com.amazon.venezia",
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                null  // Allow null for testing/sideload
            )
            installer !in allowedInstallers
        } catch (e: Exception) {
            false // Can't check → assume OK
        }
    }
}
```

### C2. Integrate Integrity Checks at App Startup

In `SafeHarborApp.kt` (the Application class), run the integrity check on startup.
Do NOT crash the app — instead, disable sensitive features and warn the user:

```kotlin
// In SafeHarborApp.onCreate():
val integrity = IntegrityChecker.check(this)
if (integrity.riskLevel == IntegrityChecker.RiskLevel.HIGH) {
    // Store flag — ViewModels check this and show warning banner
    integrityRiskHigh = true
    // Disable: API key storage, cloud analysis (keys could be stolen)
    // Enable: on-device analysis only (SLM classifier still works)
}
if (integrity.isDebuggerAttached) {
    // In release builds only: clear sensitive data from memory
    if (!BuildConfig.DEBUG) {
        // Wipe in-memory API keys
    }
}
```

### C3. Add Native Library for Critical Checks (Optional but Recommended)

For stronger anti-tampering, move the integrity check into a native (C/C++) library
that's harder to decompile than Kotlin bytecode:

Add to `app/build.gradle.kts`:
```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}
```

Create `app/src/main/cpp/integrity_native.cpp` with the root detection and
signature verification in C. This makes it significantly harder for a reverse
engineer to bypass the checks by simply modifying Smali code.

**If you don't have NDK set up, skip this step.** The Kotlin IntegrityChecker
from C1 is sufficient for Phase 1. Native hardening is a Phase 2 enhancement.

### C4. Add Signature Verification

Add to IntegrityChecker:

```kotlin
fun verifySignature(context: Context, expectedSha256: String): Boolean {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
        }

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        signatures?.any { sig ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(sig.toByteArray())
            val hexHash = hash.joinToString("") { "%02x".format(it) }
            hexHash == expectedSha256.lowercase()
        } ?: false
    } catch (e: Exception) {
        false
    }
}
```

Store the expected signing certificate SHA-256 hash in BuildConfig:
```kotlin
// In build.gradle.kts defaultConfig:
buildConfigField("String", "EXPECTED_SIGNATURE", "\"YOUR_SIGNING_CERT_SHA256_HERE\"")
```

### C5. Add FLAG_SECURE to Sensitive Screens

Prevent screen capture and recent-apps preview of sensitive screens. Add to
`MainActivity.kt`:

```kotlin
// Set on all windows:
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

Or more granular: only apply FLAG_SECURE when viewing Settings screen (API keys),
Chat screen (conversation content), or Message detail (scam content).

---

## Part D — Easy Test Distribution (Zero-Config for Testers)

### D1. Add Built-In Demo Mode

Create `app/src/main/java/com/safeharborsecurity/app/util/DemoMode.kt`:

```kotlin
package com.safeharborsecurity.app.util

/**
 * Demo mode allows testers to use the app WITHOUT any API keys.
 * When no Claude API key is set, the app automatically uses:
 * - On-device SLM classifier for all scam detection (Part A)
 * - Android built-in TTS instead of ElevenLabs
 * - Pre-loaded sample data for demonstration
 *
 * The app is fully functional in demo mode — just without cloud AI
 * conversation and premium voice quality.
 */
object DemoMode {
    fun isActive(claudeApiKey: String?, elevenLabsKey: String?): Boolean {
        return claudeApiKey.isNullOrBlank()
    }

    // Demo mode capabilities
    val capabilities = mapOf(
        "scam_detection" to true,       // ✅ On-device SLM
        "sms_scanning" to true,         // ✅ On-device SLM
        "url_checking" to true,         // ✅ On-device pattern matching
        "qr_scanning" to true,          // ✅ ML Kit (no API key)
        "app_checking" to true,         // ✅ On-device (no API key)
        "room_scanning" to true,        // ✅ All sensors (no API key)
        "news_feed" to true,            // ✅ RSS feeds (no API key)
        "family_alerts" to true,        // ✅ Local SMS (no API key)
        "voice_chat" to false,          // ❌ Needs Claude API key
        "premium_voice" to false,       // ❌ Needs ElevenLabs key
        "cloud_verification" to false,  // ❌ Needs Claude API key
        "image_analysis" to false       // ❌ Needs Claude API key for vision
    )
}
```

### D2. Update Settings Screen — API Key Entry as "Optional Enhancement"

Modify `SettingsScreen.kt` to reframe API keys as optional:

Instead of:
> "Enter your Claude API Key" (required-sounding)

Show:
> **Safe Companion works out of the box!**
> Scam detection, message scanning, and all safety features work
> without any setup.
>
> **Optional: Unlock Premium Features**
> Add a Claude API key for AI-powered conversation and deeper analysis.
> Add an ElevenLabs key for premium natural voices.
>
> [Add Claude API Key (Optional)]
> [Add ElevenLabs Key (Optional)]

### D3. Add First-Run Demo Content

When the app first opens with no data, seed it with sample content so testers
can immediately see how everything works:

Create `app/src/main/java/com/safeharborsecurity/app/util/SampleDataSeeder.kt`:

```kotlin
package com.safeharborsecurity.app.util

import com.safeharborsecurity.app.data.local.entity.SafetyCheckResultEntity

object SampleDataSeeder {
    fun getSampleAlerts(): List<SafetyCheckResultEntity> = listOf(
        SafetyCheckResultEntity(
            id = "demo_1",
            contentType = "SMS",
            content = "URGENT: Your bank account has been compromised! Click here immediately to verify: http://bank-secure-verify.tk/login",
            sender = "+18005551234",
            verdict = "DANGEROUS",
            confidence = 0.92f,
            explanation = "This message uses urgency tactics and contains a suspicious URL on a free domain (.tk). Legitimate banks never send links like this.",
            timestamp = System.currentTimeMillis() - 3600000
        ),
        SafetyCheckResultEntity(
            id = "demo_2",
            contentType = "SMS",
            content = "Hi Grandma, I lost my phone and this is my new number. Can you send me $500 for an emergency? I'll pay you back.",
            sender = "+17205559876",
            verdict = "SUSPICIOUS",
            confidence = 0.78f,
            explanation = "This is a common 'grandparent scam' where someone pretends to be a family member. Verify their identity by calling their old number or asking a question only they'd know.",
            timestamp = System.currentTimeMillis() - 7200000
        ),
        SafetyCheckResultEntity(
            id = "demo_3",
            contentType = "SMS",
            content = "Your prescription is ready for pickup at CVS. Reply STOP to opt out.",
            sender = "+12145550000",
            verdict = "SAFE",
            confidence = 0.85f,
            explanation = "This appears to be a legitimate pharmacy notification. No suspicious links or urgency tactics detected.",
            timestamp = System.currentTimeMillis() - 1800000
        )
    )
}
```

On first launch, insert these into Room so the home screen shows real-looking
alerts that demonstrate each verdict type. Add a "Sample Data" badge so testers
know these aren't real alerts. Clear them when the user dismisses or when real
alerts come in.

### D4. Build Flavor for Testing

Add a `beta` build flavor in `build.gradle.kts`:

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("standard") {
        dimension = "distribution"
        // Normal release
    }
    create("beta") {
        dimension = "distribution"
        applicationIdSuffix = ".beta"
        versionNameSuffix = "-beta"
        // Pre-seed with demo data
        buildConfigField("Boolean", "DEMO_MODE_DEFAULT", "true")
        buildConfigField("Boolean", "SHOW_DEBUG_INFO", "true")
    }
}
```

Build the beta APK with: `./gradlew assembleBetaRelease`

The beta flavor:
- Auto-seeds demo data on first launch
- Shows a "BETA" badge in the app bar
- Includes a hidden "Debug Info" screen (triple-tap app version in Settings)
  showing: device model, Android version, permissions granted, integrity check
  results, SLM classifier stats

### D5. Create a Simple Tester README

Create `app/TESTER_README.md`:

```markdown
# Safe Companion — Beta Testing Guide

## Installation
1. Install the APK on your Android phone (Android 8.0+)
2. Open Safe Companion
3. Walk through the permission setup (the app guides you)
4. **That's it!** No API keys or accounts needed.

## What Works Without API Keys
- ✅ Scam text/SMS detection (on-device AI)
- ✅ Suspicious URL checking
- ✅ QR code safety scanning
- ✅ App safety checker ("What's This App?")
- ✅ Room scanner (WiFi, Bluetooth, IR, magnetic, ultrasonic)
- ✅ Security news feed
- ✅ Family safety alerts (SMS-based)
- ✅ Daily safety tips
- ✅ Home screen widget

## Optional: Unlock Voice Chat
To enable the AI voice assistant (Grace, James, Sophie, George):
1. Go to Settings (gear icon)
2. Tap "Add Claude API Key"
3. Get a key from https://console.anthropic.com (free trial available)
4. Paste it in

## Optional: Premium Voice Quality
For natural-sounding voices:
1. Go to Settings
2. Tap "Add ElevenLabs Key"
3. Get a key from https://elevenlabs.io (free tier available)

## Testing Checklist
- [ ] App opens and shows home screen
- [ ] Sample alerts visible (marked as demo data)
- [ ] "Is This Safe?" → type a suspicious message → see verdict
- [ ] "Is This Safe?" → scan QR code → see verdict
- [ ] "What's This App?" → shows installed apps → check one
- [ ] "Scan This Room" → all available scans complete
- [ ] Settings → all options accessible
- [ ] Notification appears for sample suspicious message

## Reporting Issues
Take a screenshot and send to: [your contact info]
```

---

## Part E — Final Security Cleanup & Build Verification

### E1. Disable android:debuggable in Release

This should already be automatic (AGP sets it), but verify explicitly in the
release build type:

```kotlin
buildTypes {
    release {
        isDebuggable = false
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### E2. Deep Link Validation

Add intent verification for the `safeharbor://` deep links to prevent external
apps from injecting malicious data:

In `MainActivity.kt` where deep link intents are handled:
```kotlin
// Validate deep link data before processing
private fun handleDeepLink(intent: Intent) {
    val data = intent.data ?: return
    if (data.scheme != "safeharbor") return

    // Only allow known hosts/paths
    val allowedPaths = setOf("message", "alert", "settings", "scan")
    val path = data.host ?: return
    if (path !in allowedPaths) return

    // Sanitize parameters
    val id = data.getQueryParameter("id")?.take(64)?.filter { it.isLetterOrDigit() || it == '-' }
    // ... navigate to appropriate screen
}
```

### E3. Build and Verify

Run these commands in sequence:
```bash
./gradlew clean
./gradlew assembleDebug          # Verify debug build works
./gradlew assembleBetaRelease    # Verify beta flavor builds (if flavor added)
./gradlew assembleRelease        # Verify release with full R8 obfuscation
```

Fix ALL errors. Then verify:
1. Debug build: app opens, all features work, demo data appears
2. Release build: app opens, no crashes from missing ProGuard rules
3. Check APK size: release should be smaller than debug (R8 shrinking)
4. Verify R8 output: check `app/build/outputs/mapping/release/mapping.txt`
   exists (confirms obfuscation ran)

### E4. Update Status Table

After all parts complete, update `CLAUDE.md` status table. Add these items:

| ID | Feature / Fix | Status |
|----|--------------|--------|
| 62 | On-Device SLM Scam Classifier | DONE |
| 63 | Hybrid Analysis (local-first, cloud fallback) | DONE |
| 64 | Security Audit Fixes (logging, keystore, cert pinning) | DONE |
| 65 | Anti-Reverse-Engineering (root/emulator/tamper detection) | DONE |
| 66 | Complete ProGuard/R8 Rules | DONE |
| 67 | Beta Test Distribution Mode (zero-config) | DONE |
| 68 | Demo Mode with Sample Data | DONE |
| Fix 42 | Remove debug logging / API key exposure in logs | DONE |
| Fix 43 | KeystoreManager plaintext fallback vulnerability | DONE |
| Fix 44 | OkHttp BODY logging in release builds | DONE |

---

## Key Principles

1. **Local-first, cloud-optional.** The app must be fully useful with ZERO API keys.
   On-device SLM handles 80% of scam detection. Claude Haiku is a premium upgrade.

2. **Graceful degradation, not hard blocks.** Rooted device? Warn the user but
   don't crash. No API key? Use local classifier. No network? Still scan locally.

3. **Defense in depth.** R8 obfuscation + runtime integrity checks + cert pinning +
   encrypted storage + debug log stripping. No single point of failure.

4. **Tester-friendly.** Hand someone the APK. They install it. It works. No
   Anthropic account, no ElevenLabs signup, no configuration screens to puzzle over.
