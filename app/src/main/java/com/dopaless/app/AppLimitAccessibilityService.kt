package com.dopaless.app

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppLimitAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var foregroundSince = 0L
    private val poll = object : Runnable {
        override fun run() {
            val pkg = foregroundPackage ?: return
            if (enforceLimit(pkg)) {
                foregroundPackage = null
                return
            }
            handler.postDelayed(this, 10_000L)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") {
            foregroundPackage = null
            handler.removeCallbacks(poll)
            return
        }
        if (pkg != foregroundPackage) {
            foregroundPackage = pkg
            foregroundSince = System.currentTimeMillis()
            handler.removeCallbacks(poll)
            if (isLimited(pkg)) {
                if (!enforceLimit(pkg)) handler.postDelayed(poll, 10_000L)
            }
        }
    }

    private fun isLimited(pkg: String): Boolean {
        val limits = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
            .getStringSet("app_limits", emptySet()).orEmpty()
        return limits.any { it.substringBefore("|") == pkg }
    }

    private fun enforceLimit(pkg: String): Boolean {
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        val bypass = p.getLong("extra_" + pkg, 0L)
        if (bypass > System.currentTimeMillis()) return false
        val limit = p.getStringSet("app_limits", emptySet()).orEmpty().firstNotNullOfOrNull {
            val parts = it.split("|")
            if (parts.size == 2 && parts[0] == pkg) parts[1].toIntOrNull() else null
        } ?: return false
        val ops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        if (ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) != android.app.AppOpsManager.MODE_ALLOWED) return false
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val stats = (getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager).queryAndAggregateUsageStats(start, System.currentTimeMillis())
        val accumulated = stats[pkg]?.totalTimeInForeground ?: 0L
        val currentSession = (System.currentTimeMillis() - foregroundSince).coerceAtLeast(0L)
        if (accumulated + currentSession < limit * 60_000L) return false
        val last = p.getString("blocked_package", null)
        if (last == pkg && System.currentTimeMillis() - p.getLong("blocked_at", 0L) < 3_000L) return true
        p.edit().putString("blocked_package", pkg).putLong("blocked_at", System.currentTimeMillis()).apply()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            startActivity(block)
            true
        } catch (_: Exception) {
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
