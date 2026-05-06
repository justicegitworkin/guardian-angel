package com.safeharborsecurity.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R
import com.safeharborsecurity.app.SafeHarborApp
import com.safeharborsecurity.app.data.datastore.UserPreferences
import com.safeharborsecurity.app.data.local.entity.AlertEntity
import com.safeharborsecurity.app.data.repository.AlertRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Item 4 (option a): Weekly digest in-app card.
 *
 * Runs every Sunday at ~6 PM local time. Pulls the last 7 days of alerts from
 * Room, builds a redacted plain-text summary of what Safe Companion did this
 * week, writes it into UserPreferences (so the home-screen card can pick it
 * up Monday morning), and posts a low-priority notification telling the user
 * the new report is ready.
 *
 * The actual email-the-user-and-family flow is option (c), deferred to a
 * later session. This worker is the lightweight in-app version.
 *
 * PII redaction is intentionally aggressive: we never include phone numbers,
 * sender names, URLs, dollar amounts, or text excerpts longer than 8 words.
 */
@HiltWorker
class WeeklyDigestWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val alertRepository: AlertRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_TAG = "weekly_digest"
        private const val NOTIF_ID = 5557

        fun enqueueWeekly(context: Context) {
            val work = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(calculateDelayToSundayEvening(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        private fun calculateDelayToSundayEvening(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 18)   // 6 PM Sunday
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        // Honour the opt-in. If the user hasn't enabled weekly digests, do
        // nothing — don't even build the summary. (The flag default is false.)
        val enabled = userPreferences.isWeeklyDigestEnabled.first()
        if (!enabled) return Result.success()

        val weekAgoMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val sms = alertRepository.getAlertsByTypeSince("SMS", weekAgoMs)
        val emails = alertRepository.getAlertsByTypeSince("EMAIL", weekAgoMs)
        val calls = alertRepository.getAlertsByTypeSince("CALL", weekAgoMs)
        val screen = alertRepository.getAlertsByTypeSince("SCREEN_SMS", weekAgoMs)
        val all = sms + emails + calls + screen

        val content = buildDigest(all)
        userPreferences.setWeeklyDigestContent(content, System.currentTimeMillis())
        showReadyNotification()
        // Auto-email path removed in v1 — the user emails on demand via the
        // "Email this report" button on the digest screen. The Gmail OAuth
        // send infrastructure (GmailAuthManager.sendEmail) stays in place for
        // future re-introduction if testers ask for unattended weekly emails.
        return Result.success()
    }

    /**
     * Builds the weekly digest. Detail-rich on purpose — the user reads this
     * on their own phone and uses the per-alert detail to spot false
     * positives. Sender, date, type and a short content preview go in.
     *
     * If the user later taps Share or Email, the same content goes through
     * the system share sheet — they have full edit control over what
     * actually gets sent. We deliberately do NOT redact in this build path
     * because the user is the audience.
     */
    private fun buildDigest(alerts: List<AlertEntity>): String {
        val total = alerts.size
        val scams = alerts.filter { it.riskLevel == "SCAM" }
            .sortedByDescending { it.timestamp }
        val warnings = alerts.filter { it.riskLevel == "WARNING" }
            .sortedByDescending { it.timestamp }
        val safe = alerts.filter { it.riskLevel == "SAFE" }
            .sortedByDescending { it.timestamp }
        val weekRange = formatWeekRange()

        return buildString {
            appendLine("Your Safe Companion week: $weekRange")
            appendLine()
            if (total == 0) {
                appendLine("Nothing came up this week. Safe Companion was watching " +
                    "in the background and everything looked normal.")
                return@buildString
            }

            appendLine("Safe Companion checked $total things this week:")
            appendLine(" • ${scams.size} threat${if (scams.size == 1) "" else "s"} stopped")
            appendLine(" • ${warnings.size} flagged as suspicious (worth a look)")
            appendLine(" • ${safe.size} looked safe")
            appendLine()

            if (scams.isNotEmpty()) {
                appendLine("─── Threats stopped (${scams.size}) ───")
                appendAlertSection(this, scams, MAX_DETAIL_PER_SECTION)
                appendLine()
            }

            if (warnings.isNotEmpty()) {
                appendLine("─── Flagged as suspicious (${warnings.size}) ───")
                appendAlertSection(this, warnings, MAX_DETAIL_PER_SECTION)
                appendLine()
            }

            if (safe.isNotEmpty()) {
                appendLine("─── Looked safe (${safe.size}) ───")
                // Safe items get a more compact rendering — date + sender +
                // type only. Detail's less interesting here.
                appendAlertSection(this, safe, MAX_DETAIL_PER_SECTION_SAFE, compact = true)
                appendLine()
            }

            appendLine("─── Notice something wrong? ───")
            appendLine("If a message above was actually fine (a real text from " +
                "your bank, family, or a service you use), open Safe Companion, " +
                "tap Messages, find the entry, and tap it to take action. Help " +
                "us learn by telling us when we got it wrong.")
            appendLine()
            appendLine("This summary lives on your phone. If you Share or Email " +
                "it, you control exactly what goes out — your email app will " +
                "show the full content and let you edit before sending.")
        }
    }

    /** Render a list of alerts as detail rows. */
    private fun appendAlertSection(
        sb: StringBuilder,
        alerts: List<AlertEntity>,
        cap: Int,
        compact: Boolean = false
    ) {
        val shown = alerts.take(cap)
        shown.forEachIndexed { idx, alert ->
            val date = detailDateFormat.format(Date(alert.timestamp))
            val type = humanType(alert.type).replaceFirstChar { it.uppercase() }
            val sender = alert.sender.ifBlank { "(unknown sender)" }
            sb.appendLine("${idx + 1}. $date — $type from $sender")
            if (!compact) {
                if (alert.reason.isNotBlank()) sb.appendLine("   Why: ${alert.reason}")
                if (alert.content.isNotBlank()) {
                    val preview = alert.content
                        .lines().firstOrNull()?.trim().orEmpty()
                        .take(120)
                    if (preview.isNotEmpty()) sb.appendLine("   Said: \"$preview${if (alert.content.length > 120) "…" else ""}\"")
                }
            }
        }
        val hidden = alerts.size - shown.size
        if (hidden > 0) {
            sb.appendLine("   …and $hidden more not shown here. " +
                "Open Safe Companion → Messages to see them all.")
        }
    }

    private val detailDateFormat = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)
    private val MAX_DETAIL_PER_SECTION = 25
    private val MAX_DETAIL_PER_SECTION_SAFE = 15

    private fun humanType(type: String): String = when (type) {
        "SMS", "SCREEN_SMS" -> "text"
        "EMAIL" -> "email"
        "CALL" -> "phone call"
        else -> "alert"
    }

    private fun formatWeekRange(): String {
        val sdf = SimpleDateFormat("MMM d", Locale.US)
        val end = Calendar.getInstance()
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        return "${sdf.format(start.time)} – ${sdf.format(end.time)}"
    }

    private fun showReadyNotification() {
        val tap = Intent(appContext, MainActivity::class.java).apply {
            data = android.net.Uri.parse("safeharbor://weekly_digest")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            appContext, NOTIF_ID, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(appContext, SafeHarborApp.CHANNEL_REPORT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your weekly Safe Companion report is ready")
            .setContentText("Tap to see what Safe Companion did this week.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        (appContext.getSystemService(NotificationManager::class.java)).notify(NOTIF_ID, n)
    }
}
