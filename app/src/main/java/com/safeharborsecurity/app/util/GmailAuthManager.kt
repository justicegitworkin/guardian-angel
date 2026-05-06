package com.safeharborsecurity.app.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GmailAuth"
private const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
// Item 4 (option c): gmail.send for the Weekly Digest worker.
private const val GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send"
// Silent Guardian Path 2: gmail.modify lets us add/remove labels and trash
// messages. We use it to apply a "SafeCompanion/Quarantined" label to scam
// emails and remove them from INBOX without deleting them — the user can
// review the Quarantined folder later if we got something wrong.
private const val GMAIL_MODIFY_SCOPE = "https://www.googleapis.com/auth/gmail.modify"
private const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
private const val QUARANTINE_LABEL_NAME = "SafeCompanion/Quarantined"

data class GmailMessage(
    val id: String,
    val sender: String,
    val subject: String,
    val snippet: String,
    val date: Long
)

@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    private var googleSignInClient: GoogleSignInClient? = null

    /**
     * Check if Google Sign-In is properly configured.
     * Returns false if google-services.json is missing or OAuth client ID is not set up.
     */
    fun isGoogleSignInConfigured(): Boolean {
        val clientId = getWebClientId()
        val configured = clientId != null
        Log.d(TAG, "isGoogleSignInConfigured: configured=$configured | clientId=${clientId?.take(20) ?: "null"} | packageName=${appContext.packageName}")
        return configured
    }

    /**
     * Debug diagnostic — returns human-readable config status for on-screen display.
     */
    fun getConfigDiagnostics(): String {
        val sb = StringBuilder()
        sb.appendLine("--- Gmail Config Diagnostics ---")
        sb.appendLine("packageName: ${appContext.packageName}")

        // Check google-services.json resource
        val resId = try {
            appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        } catch (_: Exception) { 0 }
        sb.appendLine("default_web_client_id resId: $resId")

        if (resId != 0) {
            val clientId = try { appContext.getString(resId) } catch (_: Exception) { "(error)" }
            sb.appendLine("  length: ${clientId.length}")
            sb.appendLine("  preview: ${clientId.take(30)}...")
        } else {
            sb.appendLine("  NOT FOUND (no google-services plugin)")
        }

        // Check fallback resource
        val fallbackId = try {
            appContext.resources.getIdentifier("gmail_web_client_id", "string", appContext.packageName)
        } catch (_: Exception) { 0 }
        sb.appendLine("gmail_web_client_id resId: $fallbackId")

        if (fallbackId != 0) {
            val clientId = try { appContext.getString(fallbackId) } catch (_: Exception) { "(error)" }
            sb.appendLine("  length: ${clientId.length}")
            sb.appendLine("  preview: ${clientId.take(30)}...")
        }

        val resolved = getWebClientId()
        sb.appendLine("")
        sb.appendLine("resolved clientId: ${resolved?.take(30) ?: "null"}")
        sb.appendLine("isConfigured: ${resolved != null}")

        // Check for existing signed-in account
        val existing = GoogleSignIn.getLastSignedInAccount(appContext)
        sb.appendLine("")
        sb.appendLine("existing account: ${existing?.email ?: "none"}")

        sb.appendLine("")
        sb.appendLine("SHA1: AB:D6:28:12:A7:62:A6:06")
        sb.appendLine("      :95:33:5A:8B:5A:08:34:9B")
        sb.appendLine("      :83:F6:57:2B")
        sb.appendLine("")
        sb.appendLine("Verify this SHA1 matches your")
        sb.appendLine("Android OAuth client in Google")
        sb.appendLine("Cloud Console > Credentials.")

        return sb.toString()
    }

    /**
     * Get the web client ID from resources (google-services.json) or from
     * R.string.gmail_web_client_id fallback in strings.xml.
     */
    private fun getWebClientId(): String? {
        // Try google-services.json generated resource first
        val resId = appContext.resources.getIdentifier(
            "default_web_client_id", "string", appContext.packageName
        )
        if (resId != 0) {
            val id = appContext.getString(resId)
            if (id.isNotBlank() && !id.contains("YOUR_")) return id
        }
        // Try manual fallback in strings.xml
        val fallbackId = appContext.resources.getIdentifier(
            "gmail_web_client_id", "string", appContext.packageName
        )
        if (fallbackId != 0) {
            val id = appContext.getString(fallbackId)
            if (id.isNotBlank() && !id.contains("YOUR_")) return id
        }
        return null
    }

    fun getSignInIntent(): Intent {
        val clientId = getWebClientId()
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GMAIL_READONLY_SCOPE))
            .requestScopes(Scope(GMAIL_SEND_SCOPE))
            .requestScopes(Scope(GMAIL_MODIFY_SCOPE))

        if (clientId != null) {
            builder.requestIdToken(clientId)
            builder.requestServerAuthCode(clientId)
            Log.d(TAG, "Using web client ID: ${clientId.take(20)}...")
        } else {
            Log.w(TAG, "No web client ID found — sign-in may fail with error 10")
        }

        googleSignInClient = GoogleSignIn.getClient(appContext, builder.build())
        return googleSignInClient!!.signInIntent
    }

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Sign-in success: ${account.email} | idToken=${account.idToken?.take(20)} | serverAuthCode=${account.serverAuthCode?.take(20)}")
            Result.success(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed: code=${e.statusCode} | message=${e.message} | webClientId=${getWebClientId()?.take(20)}", e)
            Result.failure(Exception(getFriendlySignInError(e.statusCode)))
        }
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(appContext)
    }

    suspend fun signOut() {
        googleSignInClient?.signOut()
    }

    suspend fun fetchRecentEmails(
        account: GoogleSignInAccount,
        maxResults: Int = 30,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<GmailMessage>> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken(account)
                ?: return@withContext Result.failure(Exception("Could not get access token. Please try reconnecting your Gmail account."))

            // Fetch message IDs from last 7 days
            val listUrl = "$GMAIL_API_BASE/messages?maxResults=$maxResults&q=newer_than:7d"
            val listRequest = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val listResponse = okHttpClient.newCall(listRequest).execute()
            if (!listResponse.isSuccessful) {
                val code = listResponse.code
                listResponse.close()
                return@withContext Result.failure(Exception(
                    if (code == 401) "Gmail access expired. Please reconnect your account."
                    else "Could not reach Gmail. Please check your internet connection."
                ))
            }

            val listBody = listResponse.body?.string() ?: "{}"
            listResponse.close()
            val listJson = JSONObject(listBody)

            val messagesArray = if (listJson.has("messages")) listJson.getJSONArray("messages") else null
            if (messagesArray == null || messagesArray.length() == 0) {
                return@withContext Result.success(emptyList())
            }

            val totalCount = messagesArray.length()
            val emails = mutableListOf<GmailMessage>()

            for (i in 0 until totalCount) {
                val msgId = messagesArray.getJSONObject(i).getString("id")
                onProgress(i + 1, totalCount)

                val msgRequest = Request.Builder()
                    .url("$GMAIL_API_BASE/messages/$msgId?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                try {
                    val msgResponse = okHttpClient.newCall(msgRequest).execute()
                    if (msgResponse.isSuccessful) {
                        val msgBody = msgResponse.body?.string() ?: "{}"
                        msgResponse.close()
                        val msgJson = JSONObject(msgBody)

                        val headers = msgJson.optJSONObject("payload")?.optJSONArray("headers")
                        var sender = ""
                        var subject = ""
                        if (headers != null) {
                            for (h in 0 until headers.length()) {
                                val header = headers.getJSONObject(h)
                                when (header.getString("name")) {
                                    "From" -> sender = header.getString("value")
                                    "Subject" -> subject = header.getString("value")
                                }
                            }
                        }

                        val snippet = msgJson.optString("snippet", "")
                        val internalDate = msgJson.optLong("internalDate", 0L)

                        emails.add(GmailMessage(
                            id = msgId,
                            sender = sender,
                            subject = subject,
                            snippet = snippet,
                            date = internalDate
                        ))
                    } else {
                        msgResponse.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch message $msgId", e)
                }
            }

            Result.success(emails)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch emails", e)
            Result.failure(Exception("Could not read your emails. Please check your internet and try again."))
        }
    }

    private fun getAccessToken(account: GoogleSignInAccount): String? {
        return try {
            val scope = "oauth2:$GMAIL_READONLY_SCOPE"
            com.google.android.gms.auth.GoogleAuthUtil.getToken(appContext, account.account!!, scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get access token", e)
            null
        }
    }

    /**
     * Item 4 (option c): get an OAuth2 token specifically scoped for sending
     * email. Separate from the readonly token because Google issues different
     * tokens per scope — we don't want the read-token leaking the send right.
     */
    private fun getSendAccessToken(account: GoogleSignInAccount): String? {
        return try {
            val scope = "oauth2:$GMAIL_SEND_SCOPE"
            com.google.android.gms.auth.GoogleAuthUtil.getToken(appContext, account.account!!, scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get send access token (user may need to re-auth)", e)
            null
        }
    }

    /**
     * Item 4 (option c): Send an email from the signed-in Gmail account to
     * [to] (and optional [cc]) via the Gmail API.
     *
     * Builds an RFC 822 message, base64-URL-encodes it, and POSTs to
     * /gmail/v1/users/me/messages/send. The signed-in user is the From: —
     * recipients see the email coming from the user's own Gmail address,
     * which is what we want for the weekly Safe Companion digest.
     *
     * Returns Result.failure with a friendly message if the user hasn't
     * granted the gmail.send scope, the token has expired, or the network
     * call fails.
     */
    suspend fun sendEmail(
        account: GoogleSignInAccount,
        to: String,
        cc: String? = null,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getSendAccessToken(account)
                ?: return@withContext Result.failure(
                    Exception("Could not get permission to send email. Please reconnect Gmail.")
                )

            val from = account.email
                ?: return@withContext Result.failure(Exception("No Gmail address on this account."))

            // Build the RFC 822 message. Plain-text body so it renders cleanly
            // for older email clients and screen readers — no HTML.
            val rfcBuilder = StringBuilder().apply {
                append("From: ").append(from).append("\r\n")
                append("To: ").append(to).append("\r\n")
                if (!cc.isNullOrBlank()) {
                    append("Cc: ").append(cc).append("\r\n")
                }
                append("Subject: ").append(encodeHeaderIfNeeded(subject)).append("\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: 8bit\r\n")
                append("\r\n")
                append(body)
            }
            val rfcBytes = rfcBuilder.toString().toByteArray(Charsets.UTF_8)
            val raw = android.util.Base64.encodeToString(
                rfcBytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )

            val payload = JSONObject().put("raw", raw).toString()
            val request = Request.Builder()
                .url("$GMAIL_API_BASE/messages/send")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    payload
                ))
                .build()

            val response = okHttpClient.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    Log.d(TAG, "Sent weekly digest email to $to")
                    Result.success(Unit)
                } else {
                    val code = response.code
                    val errBody = response.body?.string()?.take(200) ?: ""
                    Log.w(TAG, "Gmail send failed: code=$code body=$errBody")
                    Result.failure(Exception(
                        when (code) {
                            401, 403 -> "Gmail permission expired. Please reconnect Gmail in Settings."
                            429 -> "Gmail is busy right now. We'll try again next time."
                            else -> "Could not send the email (Gmail returned $code)."
                        }
                    ))
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendEmail threw", e)
            Result.failure(Exception("Could not send the email. Please check your internet."))
        }
    }

    /**
     * Silent Guardian Path 2: Quarantine a scam email.
     *
     * Apply the "SafeCompanion/Quarantined" label and remove from INBOX. The
     * email is NOT deleted — it lives in the user's All Mail and the
     * Quarantined label, so they can review and unquarantine if we got it
     * wrong. (Deleting outright would be too destructive for a beta auto-
     * action; deletion is a better-trusted-product feature.)
     *
     * Returns Result.failure with a friendly message if the user hasn't
     * granted gmail.modify scope, the network call fails, or the message
     * doesn't exist.
     */
    suspend fun quarantineEmail(
        account: GoogleSignInAccount,
        messageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getModifyAccessToken(account)
                ?: return@withContext Result.failure(
                    Exception("Gmail modify permission missing — please reconnect Gmail.")
                )

            // Look up (or create) the quarantine label. Gmail API requires a
            // label ID, not a name, for the modify call.
            val labelId = getOrCreateQuarantineLabelId(token)
                ?: return@withContext Result.failure(
                    Exception("Could not create quarantine label.")
                )

            // POST /users/me/messages/{id}/modify
            //   { "addLabelIds": ["<our label>"], "removeLabelIds": ["INBOX"] }
            val payload = JSONObject().apply {
                put("addLabelIds", org.json.JSONArray().put(labelId))
                put("removeLabelIds", org.json.JSONArray().put("INBOX"))
            }.toString()

            val request = Request.Builder()
                .url("$GMAIL_API_BASE/messages/$messageId/modify")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    payload
                ))
                .build()

            val response = okHttpClient.newCall(request).execute()
            try {
                if (response.isSuccessful) {
                    Log.d(TAG, "Quarantined message $messageId")
                    Result.success(Unit)
                } else {
                    val code = response.code
                    Log.w(TAG, "Quarantine failed: code=$code")
                    Result.failure(Exception(when (code) {
                        401, 403 -> "Gmail permission expired — please reconnect Gmail."
                        404 -> "Email no longer exists in your inbox."
                        else -> "Could not quarantine email (Gmail returned $code)."
                    }))
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "quarantineEmail threw", e)
            Result.failure(Exception("Could not quarantine email: ${e.message}"))
        }
    }

    /** Fetch existing label list, find or create our quarantine label. */
    private fun getOrCreateQuarantineLabelId(token: String): String? {
        // GET /users/me/labels
        val listReq = Request.Builder()
            .url("$GMAIL_API_BASE/labels")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val listResp = okHttpClient.newCall(listReq).execute()
        try {
            if (listResp.isSuccessful) {
                val body = listResp.body?.string() ?: return null
                val labels = JSONObject(body).optJSONArray("labels") ?: return null
                for (i in 0 until labels.length()) {
                    val l = labels.getJSONObject(i)
                    if (l.optString("name") == QUARANTINE_LABEL_NAME) {
                        return l.optString("id")
                    }
                }
            }
        } finally {
            listResp.close()
        }

        // Not found — create. POST /users/me/labels with name + visibility settings.
        val createPayload = JSONObject().apply {
            put("name", QUARANTINE_LABEL_NAME)
            put("labelListVisibility", "labelShow")
            put("messageListVisibility", "show")
        }.toString()
        val createReq = Request.Builder()
            .url("$GMAIL_API_BASE/labels")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                createPayload
            ))
            .build()
        val createResp = okHttpClient.newCall(createReq).execute()
        return try {
            if (createResp.isSuccessful) {
                val body = createResp.body?.string() ?: return null
                JSONObject(body).optString("id").takeIf { it.isNotBlank() }
            } else {
                Log.w(TAG, "Could not create quarantine label: code=${createResp.code}")
                null
            }
        } finally {
            createResp.close()
        }
    }

    private fun getModifyAccessToken(account: GoogleSignInAccount): String? {
        return try {
            val scope = "oauth2:$GMAIL_MODIFY_SCOPE"
            com.google.android.gms.auth.GoogleAuthUtil.getToken(appContext, account.account!!, scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get modify access token (user may need to re-auth)", e)
            null
        }
    }

    /** RFC 2047 quote subject lines that contain non-ASCII so Unicode renders. */
    private fun encodeHeaderIfNeeded(value: String): String {
        return if (value.all { it.code < 128 }) {
            value
        } else {
            val b64 = android.util.Base64.encodeToString(
                value.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            "=?UTF-8?B?$b64?="
        }
    }

    private fun getFriendlySignInError(statusCode: Int): String {
        return when (statusCode) {
            12501 -> "Sign-in was cancelled. Please try again when you're ready."
            12502 -> "Sign-in is already in progress."
            7 -> "Could not connect to Google. Please check your internet connection."
            10 -> "Gmail is not quite ready yet. Your helper needs to complete one more setup step. Please ask them to check the app settings. (Error 10 — missing Android OAuth client in Google Cloud Console)"
            else -> "Could not sign in to Google. Please try again. (Error $statusCode)"
        }
    }
}
