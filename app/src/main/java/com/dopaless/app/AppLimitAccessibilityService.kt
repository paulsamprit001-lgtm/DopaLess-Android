package com.dopaless.app

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

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
            if (foregroundPackage == pkg) handler.postDelayed(this, 1_500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" ||
            pkg == "com.android.launcher3" || pkg == "com.miui.home") {
            foregroundPackage = null
            handler.removeCallbacks(poll)
            return
        }

        foregroundPackage = pkg
        handler.removeCallbacks(poll)
        if (isLimited(pkg)) {
            if (!enforceLimit(pkg) && foregroundPackage == pkg) handler.postDelayed(poll, 1_500L)
        }
    }

    private fun isLimited(pkg: String): Boolean =
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
            .getStringSet("app_limits", emptySet()).orEmpty()
            .any { it.substringBefore("|") == pkg }

    private fun hasUsagePermission(): Boolean = try {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun enforceLimit(pkg: String): Boolean {
        if (!isLimited(pkg) || !hasUsagePermission()) return false
        val prefs = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        if (prefs.getLong("extra_" + pkg, 0L) > System.currentTimeMillis()) return false

        val limit = prefs.getStringSet("app_limits", emptySet()).orEmpty()
            .firstNotNullOfOrNull { item ->
                val parts = item.split("|")
                if (parts.size == 2 && parts[0] == pkg) parts[1].toIntOrNull() else null
            } ?: return false

        val used = try { ScreenTimeTracker.read(this).perPackageMillis[pkg] ?: 0L }
        catch (_: Exception) { return false }
        if (used < limit * 60_000L) return false

        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        return try {
            // Move the limited app out of the foreground before showing the block screen.
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({
                try { startActivity(block) } catch (_: Exception) { }
            }, 250L)
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
        foregroundPackage = null
        super.onDestroy()
    }
}
