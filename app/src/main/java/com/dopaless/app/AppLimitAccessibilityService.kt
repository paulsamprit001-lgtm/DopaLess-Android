package com.dopaless.app

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppLimitAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private val poll = object : Runnable {
        override fun run() {
            val pkg = foregroundPackage ?: return
            if (enforceLimit(pkg)) {
                foregroundPackage = null
                return
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "com.android.launcher3") {
            foregroundPackage = null
            handler.removeCallbacks(poll)
            return
        }
        if (pkg != foregroundPackage) {
            foregroundPackage = pkg
            handler.removeCallbacks(poll)
            if (isLimited(pkg) && !enforceLimit(pkg)) handler.postDelayed(poll, 1_000L)
        }
    }

    private fun isLimited(pkg: String): Boolean =
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
            .getStringSet("app_limits", emptySet()).orEmpty()
            .any { it.substringBefore("|") == pkg }

    private fun usedToday(pkg: String): Long {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val events = (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start, now)
        val event = UsageEvents.Event()
        var sessionStart: Long? = null
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> sessionStart = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = sessionStart
                    if (began != null) total += (event.timeStamp - began).coerceAtLeast(0L)
                    sessionStart = null
                }
            }
        }
        sessionStart?.let { total += (now - it).coerceAtLeast(0L) }
        return total
    }

    private fun enforceLimit(pkg: String): Boolean {
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        if (p.getLong("extra_" + pkg, 0L) > System.currentTimeMillis()) return false
        val limit = p.getStringSet("app_limits", emptySet()).orEmpty().firstNotNullOfOrNull {
            val parts = it.split("|")
            if (parts.size == 2 && parts[0] == pkg) parts[1].toIntOrNull() else null
        } ?: return false
        val ops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        if (ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) != android.app.AppOpsManager.MODE_ALLOWED) return false
        if (usedToday(pkg) < limit * 60_000L) return false

        val last = p.getString("blocked_package", null)
        if (last == pkg && System.currentTimeMillis() - p.getLong("blocked_at", 0L) < 2_000L) return true
        p.edit().putString("blocked_package", pkg).putLong("blocked_at", System.currentTimeMillis()).apply()
        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            startActivity(block)
            true
        } catch (_: Exception) {
            p.edit().remove("blocked_package").apply()
            false
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(poll)
        foregroundPackage = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }
}
