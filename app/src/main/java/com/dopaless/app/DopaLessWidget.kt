package com.dopaless.app

import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.Calendar

class DopaLessWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DopaLessWidget::class.java))
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences("dopaless_preferences", Context.MODE_PRIVATE)
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val allowed = try {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) { false }
            val total = if (allowed) usageManager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
                .filterKeys { it != context.packageName }.values.sumOf { it.totalTimeInForeground } else 0L
            val mins = total / 60000L
            val usageText = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
            val goal = prefs.getInt("goal", 240)
            val active = prefs.getBoolean("timer_active", false)
            val paused = prefs.getBoolean("timer_paused", false)
            val end = prefs.getLong("timer_end", 0L)
            val remaining = if (paused) prefs.getLong("timer_remaining", 0L) else (end - System.currentTimeMillis()).coerceAtLeast(0L)
            val label = prefs.getString("timer_title", "Timer") ?: "Timer"
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_dopaless)
                views.setTextViewText(R.id.widget_usage, if (allowed) usageText else "Tap to enable")
                views.setTextViewText(R.id.widget_goal, "Daily goal · ${goal} min")
                views.setProgressBar(R.id.widget_progress, goal, if (allowed) mins.toInt().coerceIn(0, goal) else 0, false)
                if (active && remaining > 0L) {
                    views.setViewVisibility(R.id.widget_timer_title, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_timer, android.view.View.VISIBLE)
                    views.setTextViewText(R.id.widget_timer_title, label + if (paused) " · Paused" else "")
                    if (!paused) views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + remaining, null, true)
                    else views.setTextViewText(R.id.widget_timer, formatTimer(remaining))
                } else {
                    views.setViewVisibility(R.id.widget_timer_title, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_timer, android.view.View.GONE)
                }
                val open = android.app.PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }

        private fun formatTimer(ms: Long): String {
            val s = (ms / 1000L).toInt()
            return "%02d:%02d".format(s / 60, s % 60)
        }
    }
}
