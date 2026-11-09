package com.dopaless.app

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppLimitAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        val bypass = p.getLong("extra_" + pkg, 0L)
        if (bypass > System.currentTimeMillis()) return
        val limit = p.getStringSet("app_limits", emptySet()).orEmpty().firstNotNullOfOrNull { item ->
            val parts = item.split("|")
            if (parts.size == 2 && parts[0] == pkg) parts[1].toIntOrNull() else null
        } ?: return
        val ops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        if (ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) != android.app.AppOpsManager.MODE_ALLOWED) return
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val stats = (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryAndAggregateUsageStats(start, now)
        var activeFrom = 0L
        var activeExtra = 0L
        val events = (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start, now)
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> activeFrom = event.timeStamp
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (activeFrom > 0L) activeExtra += (event.timeStamp - activeFrom).coerceAtLeast(0L)
                    activeFrom = 0L
                }
            }
        }
        if (activeFrom > 0L) activeExtra += (now - activeFrom).coerceAtLeast(0L)
        val used = (stats[pkg]?.totalTimeInForeground ?: 0L).coerceAtLeast(activeExtra)
        if (used < limit * 60_000L) return
        val last = p.getString("blocked_package", null)
        if (last == pkg && System.currentTimeMillis() - p.getLong("blocked_at", 0L) < 2500L) return
        p.edit().putString("blocked_package", pkg).putLong("blocked_at", System.currentTimeMillis()).apply()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(block) } catch (_: Exception) { }
    }

    override fun onInterrupt() = Unit
}
