package com.safeharborsecurity.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.safeharborsecurity.app.MainActivity
import com.safeharborsecurity.app.R

class SafeHarborWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_SHIELDS = "com.safeharborsecurity.app.TOGGLE_SHIELDS"
        const val ACTION_PUSH_TO_TALK = "com.safeharborsecurity.app.PUSH_TO_TALK"

        /** Call this from anywhere to refresh all widgets. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SafeHarborWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SafeHarborWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE_SHIELDS -> {
                // Open app to home screen — shield toggle is handled there
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(openIntent)
            }
            ACTION_PUSH_TO_TALK -> {
                // Open app with voice input deep link
                val voiceIntent = Intent(context, MainActivity::class.java).apply {
                    data = android.net.Uri.parse("safeharbor://voice")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(voiceIntent)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_safe_harbor)

        // Read shield state from shared prefs (DataStore isn't accessible here easily)
        val prefs = context.getSharedPreferences("safeharbor_prefs", Context.MODE_PRIVATE)
        val smsShieldOn = prefs.getBoolean("sms_shield_enabled", true)
        val callShieldOn = prefs.getBoolean("call_shield_enabled", true)
        val allOn = smsShieldOn && callShieldOn

        // Update status text
        if (allOn) {
            views.setTextViewText(R.id.widget_status_text, "Protected ✅")
            views.setTextColor(R.id.widget_status_text, 0xFF388E3C.toInt())
            views.setTextViewText(R.id.widget_toggle_button, "Shields On")
        } else {
            views.setTextViewText(R.id.widget_status_text, "⚠️ Shields Paused")
            views.setTextColor(R.id.widget_status_text, 0xFFF57C00.toInt())
            views.setTextViewText(R.id.widget_toggle_button, "Turn On")
        }

        // Toggle button intent
        val toggleIntent = Intent(context, SafeHarborWidget::class.java).apply {
            action = ACTION_TOGGLE_SHIELDS
        }
        val togglePi = PendingIntent.getBroadcast(
            context, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePi)

        // Talk button intent
        val talkIntent = Intent(context, SafeHarborWidget::class.java).apply {
            action = ACTION_PUSH_TO_TALK
        }
        val talkPi = PendingIntent.getBroadcast(
            context, 1, talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_talk_button, talkPi)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
