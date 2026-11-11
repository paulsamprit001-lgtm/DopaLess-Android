package com.dopaless.app

import android.app.AppOpsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews

class DopaLessWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) update(context)
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DopaLessWidget::class.java))
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences("dopaless_preferences", Context.MODE_PRIVATE)
            val allowed = try {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) { false }
            val total = if (allowed) try { ScreenTimeTracker.read(context).totalMillis } catch (_: Exception) { 0L } else 0L
            val mins = total / 60_000L
            val usageText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            val goal = prefs.getInt("goal", 240).coerceAtLeast(1)
            val dark = prefs.getBoolean("dark", false)
            val background = if (dark) R.drawable.widget_background_dark else R.drawable.widget_background
            val primary = if (dark) android.graphics.Color.rgb(168,196,173) else android.graphics.Color.rgb(84,116,95)
            val foreground = if (dark) android.graphics.Color.rgb(231,236,231) else android.graphics.Color.rgb(32,40,32)
            val secondary = if (dark) android.graphics.Color.rgb(173,184,174) else android.graphics.Color.rgb(114,123,115)
            val active = prefs.getBoolean("timer_active", false)
            val paused = prefs.getBoolean("timer_paused", false)
            val end = prefs.getLong("timer_end", 0L)
            val remaining = if (paused) prefs.getLong("timer_remaining", 0L) else (end - System.currentTimeMillis()).coerceAtLeast(0L)
            val label = prefs.getString("timer_title", "Timer") ?: "Timer"

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_dopaless)
                views.setInt(R.id.widget_root, "setBackgroundResource", background)
                views.setTextViewText(R.id.widget_brand, "DopaLess")
                views.setTextColor(R.id.widget_brand, primary)
                views.setTextColor(R.id.widget_usage, foreground)
                views.setTextColor(R.id.widget_goal, secondary)
                views.setTextColor(R.id.widget_timer_title, primary)
                views.setTextColor(R.id.widget_timer, foreground)
                views.setTextViewText(R.id.widget_usage, if (allowed) usageText else "Enable usage access")
                views.setTextViewText(R.id.widget_goal, "Daily goal · ${goal} min")
                views.setProgressBar(R.id.widget_progress, goal, if (allowed) mins.toInt().coerceIn(0, goal) else 0, false)
                if (active && remaining > 0L) {
                    views.setViewVisibility(R.id.widget_timer_title, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_timer, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.widget_timer_title, label + if (paused) " · Paused" else "")
                    if (!paused) {
                        views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + remaining, null, true)
                        views.setChronometerCountDown(R.id.widget_timer, true)
                    } else {
                        views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime(), null, false)
                        views.setTextViewText(R.id.widget_timer, formatTimer(remaining))
                    }
                } else {
                    views.setViewVisibility(R.id.widget_timer_title, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_timer, android.view.View.GONE)
                }
                val open = android.app.PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, open)
                try { manager.updateAppWidget(id, views) } catch (_: Exception) { }
            }
        }

        private fun formatTimer(ms: Long): String {
            val seconds = (ms / 1000L).toInt()
            return "%02d:%02d".format(seconds / 60, seconds % 60)
        }
    }
}
