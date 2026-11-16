package com.dopaless.app

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppLimitAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var foregroundPackage: String? = null
    private var lastShortBlockAt = 0L

    private val poll = object : Runnable {
        override fun run() {
            val pkg = foregroundPackage ?: return
            if (isShortFeedApp(pkg) && isShortBlockingEnabled(pkg) &&
                isShortFeedVisible(pkg) && !isTemporarilyAllowed(pkg)) {
                blockShortFeed(pkg)
                return
            }
            if (isLimited(pkg) && enforceLimit(pkg)) {
                foregroundPackage = null
                return
            }
            if (foregroundPackage == pkg) handler.postDelayed(this, 800L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poll)
        foregroundPackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val accepted = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        if (!accepted) return

        val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (pkg == packageName || pkg == "com.android.systemui" ||
            pkg == "com.android.launcher3" || pkg == "com.miui.home" ||
            pkg == "com.google.android.inputmethod.latin") {
            foregroundPackage = null
            handler.removeCallbacks(poll)
            return
        }

        foregroundPackage = pkg
        handler.removeCallbacks(poll)
        if (isShortFeedApp(pkg) && isShortBlockingEnabled(pkg) &&
            isShortFeedVisible(pkg) && !isTemporarilyAllowed(pkg)) {
            blockShortFeed(pkg)
            return
        }
        if (isLimited(pkg)) {
            if (!enforceLimit(pkg) && foregroundPackage == pkg) handler.postDelayed(poll, 800L)
        } else if (isShortFeedApp(pkg)) {
            handler.postDelayed(poll, 800L)
        }
    }

    private fun isShortFeedApp(pkg: String): Boolean =
        pkg == "com.instagram.android" || pkg == "com.google.android.youtube"

    private fun isShortBlockingEnabled(pkg: String): Boolean {
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        if (!p.getBoolean("short_block_enabled", false)) return false
        return when (pkg) {
            "com.instagram.android" -> p.getBoolean("short_block_instagram", true)
            "com.google.android.youtube" -> p.getBoolean("short_block_youtube", true)
            else -> false
        }
    }

    private fun isTemporarilyAllowed(pkg: String): Boolean {
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        return p.getLong("short_allow_until_$pkg", 0L) > now ||
            p.getLong("short_pause_until", 0L) > now
    }

    private fun isShortFeedVisible(pkg: String): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        val needles = when (pkg) {
            "com.instagram.android" -> listOf(
                "clips_viewer_view_pager", "reel_viewer_root",
                "reel_viewer_container", "clips_video_container",
                "reel_viewer"
            )
            "com.google.android.youtube" -> listOf(
                "reel_player_page_container", "shorts_player",
                "reel_recycler", "shorts_container",
                "pivot_bar_shorts"
            )
            else -> emptyList()
        }
        if (needles.isEmpty()) return false
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < 450) {
            val node = stack.removeLast()
            visited++
            val id = try { node.viewIdResourceName?.lowercase().orEmpty() } catch (_: Exception) { "" }
            if (needles.any { id.contains(it) }) return true
            for (i in 0 until node.childCount) {
                try { node.getChild(i)?.let { stack.add(it) } } catch (_: Exception) { }
            }
        }
        return false
    }

    private fun blockShortFeed(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastShortBlockAt < 1_500L) return
        lastShortBlockAt = now
        val p = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        p.edit()
            .putInt("short_block_count", p.getInt("short_block_count", 0) + 1)
            .putLong("short_last_block_at", now)
            .apply()
        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .putExtra(AppLimitBlockActivity.EXTRA_SHORT_FEED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        try {
            startActivity(block)
            foregroundPackage = null
            handler.removeCallbacks(poll)
        } catch (_: Exception) {
            handler.postDelayed(poll, 1_000L)
        }
    }

    private fun isLimited(pkg: String): Boolean =
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
            .getStringSet("app_limits", emptySet()).orEmpty()
            .any { it.substringBefore("|") == pkg }

    private fun hasUsagePermission(): Boolean = try {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun enforceLimit(pkg: String): Boolean {
        if (!isLimited(pkg) || !hasUsagePermission()) return false
        val prefs = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
        if (prefs.getLong("extra_" + pkg, 0L) > System.currentTimeMillis()) return false
        val limit = prefs.getStringSet("app_limits", emptySet()).orEmpty().firstNotNullOfOrNull { item ->
            val parts = item.split("|")
            if (parts.size == 2 && parts[0] == pkg) parts[1].toIntOrNull() else null
        } ?: return false
        val used = try { ScreenTimeTracker.read(this).perPackageMillis[pkg] ?: 0L }
        catch (_: Exception) { return false }
        if (used < limit * 60_000L) return false
        val block = Intent(this, AppLimitBlockActivity::class.java)
            .putExtra(AppLimitBlockActivity.EXTRA_PACKAGE, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        return try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({
                try { startActivity(block) }
                catch (_: Exception) {
                    foregroundPackage = pkg
                    handler.removeCallbacks(poll)
                    handler.postDelayed(poll, 1_000L)
                }
            }, 300L)
            true
        } catch (_: Exception) { false }
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
