package com.dopaless.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

data class ForegroundUsageResult(
    val perPackageMillis: Map<String, Long>,
    val hourlyMillis: List<Long>
) {
    val totalMillis: Long get() = perPackageMillis.values.sum()
}

object ScreenTimeTracker {
    fun todayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun read(context: Context, from: Long = todayStart(), until: Long = System.currentTimeMillis()): ForegroundUsageResult {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Read a little before midnight so an app already open at midnight is not missed.
        val queryFrom = (from - 24L * 60L * 60L * 1000L).coerceAtLeast(0L)
        val events = manager.queryEvents(queryFrom, until)
        val event = UsageEvents.Event()
        val totals = mutableMapOf<String, Long>()
        val hourly = MutableList(24) { 0L }
        val pm = context.packageManager
        val launchable = mutableMapOf<String, Boolean>()
        fun countable(pkg: String): Boolean {
            if (pkg == context.packageName || pkg == "com.android.systemui" ||
                pkg == "com.android.launcher3" || pkg == "com.miui.home" ||
                pkg == "com.google.android.inputmethod.latin") return false
            return launchable.getOrPut(pkg) { pm.getLaunchIntentForPackage(pkg) != null }
        }
        var activePackage: String? = null
        var activeSince = 0L

        fun closeActive(at: Long) {
            val pkg = activePackage ?: return
            val end = at.coerceAtMost(until)
            val begin = activeSince.coerceAtLeast(from)
            if (countable(pkg) && end > begin) {
                val duration = end - begin
                totals[pkg] = (totals[pkg] ?: 0L) + duration
                var cursor = begin
                while (cursor < end) {
                    val hour = ((cursor - from) / 3_600_000L).toInt().coerceIn(0, 23)
                    val next = minOf(end, from + (hour + 1L) * 3_600_000L)
                    hourly[hour] += next - cursor
                    cursor = next
                }
            }
            activePackage = null
            activeSince = 0L
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (activePackage != pkg) {
                        closeActive(event.timeStamp)
                        activePackage = pkg
                        activeSince = event.timeStamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (activePackage == pkg) closeActive(event.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> closeActive(event.timeStamp)
            }
        }
        closeActive(until)
        return ForegroundUsageResult(totals.filterValues { it > 0L }, hourly)
    }
}
