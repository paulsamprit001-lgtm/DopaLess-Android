package com.dopaless.app

import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class DopaLessWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val allowed = try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
        val total = if (allowed) usageManager.queryAndAggregateUsageStats(start, System.currentTimeMillis())
            .filterKeys { it != context.packageName }.values.sumOf { it.totalTimeInForeground } else 0L
        val minutes = total / 60000L
        val time = if (minutes >= 60) (minutes / 60).toString() + "h " + (minutes % 60) + "m" else minutes.toString() + "m"
        val prefs = context.getSharedPreferences("dopaless_preferences", Context.MODE_PRIVATE)
        val goal = prefs.getInt("goal", 240)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dopaless)
            views.setTextViewText(R.id.widget_usage, if (allowed) time else "Tap to enable")
            views.setTextViewText(R.id.widget_goal, "Daily goal · " + goal + " min")
            views.setProgressBar(R.id.widget_progress, goal, if (allowed) minutes.toInt().coerceIn(0, goal) else 0, false)
            val open = PendingIntentCompat.activity(context)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DopaLessWidget::class.java))
            if (ids.isNotEmpty()) DopaLessWidget().onUpdate(context, manager, ids)
        }
    }
}

private object PendingIntentCompat {
    fun activity(context: Context): android.app.PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return android.app.PendingIntent.getActivity(
            context, 1, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}
