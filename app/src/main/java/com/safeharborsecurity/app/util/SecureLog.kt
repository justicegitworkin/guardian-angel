package com.safeharborsecurity.app.util

import android.util.Log
import com.safeharborsecurity.app.BuildConfig

/**
 * Part B1: Secure logging wrapper.
 *
 * In release builds R8 strips all calls to this object thanks to the
 * `-assumenosideeffects` rule in proguard-rules.pro.
 *
 * In debug builds, the message body is redacted to remove anything that looks
 * like an API key or an email address before logging — so developers can see
 * the shape of a request without exposing secrets in `adb logcat`.
 */
object SecureLog {
    fun d(tag: String, message: String) { if (BuildConfig.DEBUG) Log.d(tag, redact(message)) }
    fun i(tag: String, message: String) { if (BuildConfig.DEBUG) Log.i(tag, redact(message)) }
    fun w(tag: String, message: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(tag, redact(message), t)
    }
    fun e(tag: String, message: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, redact(message), t)
    }

    /** Public so callers (e.g. interceptors) can sanitise their own strings. */
    fun redact(message: String): String =
        message
            // sk-…  / sk_…  Anthropic / OpenAI style keys → keep first 4 chars
            .replace(Regex("""(sk[-_][a-zA-Z0-9]{4})[a-zA-Z0-9_-]+"""), "$1****")
            // header-style key=…, "api_key": "…", xi-api-key: …, Authorization: Bearer …
            .replace(
                Regex(
                    """((?:api[_-]?key|xi-api-key|authorization|bearer)\s*[:=]?\s*['"]?)[^\s'",]+""",
                    RegexOption.IGNORE_CASE
                ),
                "$1****"
            )
            // Emails
            .replace(Regex("""[\w.+-]+@[\w.-]+\.\w+"""), "***@***.***")
}
