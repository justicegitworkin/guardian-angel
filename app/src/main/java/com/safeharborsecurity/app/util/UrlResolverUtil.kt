package com.safeharborsecurity.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedUrl(
    val originalUrl: String,
    val finalUrl: String,
    val redirectCount: Int,
    val pageTitle: String?,
    val domain: String
)

@Singleton
class UrlResolverUtil @Inject constructor() {

    companion object {
        private const val TAG = "UrlResolver"
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Normalise a raw user input string into a valid HTTPS URL.
     * Returns null if the input doesn't look like a URL.
     */
    fun normaliseUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("www.")) return "https://$trimmed"
        if (trimmed.contains(".") && !trimmed.contains(" ")) return "https://$trimmed"

        return null
    }

    /**
     * Attempt to resolve a URL by following redirects and reading the page title.
     * Returns null on failure — callers should fall back to analysing the original URL.
     */
    suspend fun resolve(url: String): ResolvedUrl? = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normaliseUrl(url) ?: url

            Log.d(TAG, "Resolving URL: $normalizedUrl (original: $url)")

            val request = Request.Builder()
                .url(normalizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Response code: ${response.code} from ${response.request.url}")

                val finalUrl = response.request.url.toString()
                val body = response.peekBody(16_384).string()
                val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)?.trim()

                val redirectCount = response.priorResponse?.let { countRedirects(it) } ?: 0

                Log.d(TAG, "Resolved: finalUrl=$finalUrl, redirects=$redirectCount, title=$title")

                ResolvedUrl(
                    originalUrl = normalizedUrl,
                    finalUrl = finalUrl,
                    redirectCount = redirectCount,
                    pageTitle = title,
                    domain = response.request.url.host
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL resolution failed for '$url': ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun countRedirects(response: okhttp3.Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
