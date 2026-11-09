package com.dopaless.app

import android.app.*
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS = "dopaless_preferences"
private const val LIMITS = "app_limits"
private const val CHANNEL = "app_limit_alerts"

data class AppUsage(val packageName: String, val label: String, val millis: Long)
data class UsageSnapshot(val total: Long, val apps: List<AppUsage>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 901)
        }
        setContent { DopaLessApp() }
    }
}

private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun hasUsageAccess(c: Context): Boolean {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
}

private fun todayStart(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun readUsage(c: Context): UsageSnapshot {
    if (!hasUsageAccess(c)) return UsageSnapshot(0L, emptyList())
    val manager = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = c.packageManager
    val stats = manager.queryAndAggregateUsageStats(todayStart(), System.currentTimeMillis())
    val apps = stats.values.mapNotNull { s ->
        if (s.packageName == c.packageName || s.totalTimeInForeground <= 0L) return@mapNotNull null
        try {
            if (pm.getLaunchIntentForPackage(s.packageName) == null) return@mapNotNull null
            @Suppress("DEPRECATION") val info = pm.getApplicationInfo(s.packageName, 0)
            AppUsage(s.packageName, pm.getApplicationLabel(info).toString(), s.totalTimeInForeground)
        } catch (_: Exception) { null }
    }.sortedByDescending { it.millis }
    return UsageSnapshot(apps.sumOf { it.millis }, apps)
}


private fun readHourlyUsage(c: Context): List<Long> {
    if (!hasUsageAccess(c)) return List(24) { 0L }
    val startOfDay = todayStart()
    val now = System.currentTimeMillis()
    val manager = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val events = manager.queryEvents(startOfDay, now)
    val event = UsageEvents.Event()
    val active = mutableMapOf<String, Long>()
    val buckets = MutableList(24) { 0L }
    fun addInterval(from: Long, to: Long) {
        var cursor = from.coerceAtLeast(startOfDay)
        val end = to.coerceAtMost(now)
        while (cursor < end) {
            val hour = ((cursor - startOfDay) / 3_600_000L).toInt().coerceIn(0, 23)
            val pieceEnd = minOf(end, startOfDay + (hour + 1L) * 3_600_000L)
            buckets[hour] += pieceEnd - cursor
            cursor = pieceEnd
        }
    }
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        if (pkg == c.packageName || c.packageManager.getLaunchIntentForPackage(pkg) == null) continue
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> active[pkg] = event.timeStamp
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val began = active.remove(pkg)
                if (began != null) addInterval(began, event.timeStamp)
            }
        }
    }
    active.values.forEach { addInterval(it, now) }
    return buckets
}

internal fun fmt(ms: Long): String {
    val m = (ms.coerceAtLeast(0L) / 60000L).toInt()
    return if (m >= 60) (m / 60).toString() + "h " + (m % 60) + "m" else m.toString() + "m"
}

private fun getLimits(c: Context): Map<String, Int> =
    prefs(c).getStringSet(LIMITS, emptySet()).orEmpty().mapNotNull {
        val p = it.split("|")
        if (p.size != 2) null else p[0] to (p[1].toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

private fun putLimit(c: Context, pkg: String, minutes: Int?) {
    val m = getLimits(c).toMutableMap()
    if (minutes == null) m.remove(pkg) else m[pkg] = minutes
    prefs(c).edit().putStringSet(LIMITS, m.map { it.key + "|" + it.value }.toSet()).apply()
}

private fun checkLimitAlerts(c: Context, snapshot: UsageSnapshot) {
    val limits = getLimits(c)
    if (limits.isEmpty()) return
    val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "App time limits", NotificationManager.IMPORTANCE_DEFAULT))
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    snapshot.apps.forEach { app ->
        val limit = limits[app.packageName] ?: return@forEach
        if (app.millis < limit * 60000L) return@forEach
        val key = "alert_" + app.packageName + "_" + date
        if (prefs(c).getBoolean(key, false)) return@forEach
        val permitted = Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permitted) {
            val pi = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(c, CHANNEL)
            n.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(app.label + " limit reached")
                .setContentText("Used " + fmt(app.millis) + " today").setContentIntent(pi).setAutoCancel(true)
            nm.notify(app.packageName.hashCode(), n.build())
            prefs(c).edit().putBoolean(key, true).apply()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DopaLessApp() {
    val c = LocalContext.current
    val p = remember { prefs(c) }
    var tab by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(p.getBoolean("dark", false)) }
    var goal by remember { mutableIntStateOf(p.getInt("goal", 240)) }
    var allowed by remember { mutableStateOf(hasUsageAccess(c)) }
    var usage by remember { mutableStateOf(UsageSnapshot(0L, emptyList())) }
    var hourly by remember { mutableStateOf(List(24) { 0L }) }
    var limits by remember { mutableStateOf(getLimits(c)) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, tick) {
        allowed = hasUsageAccess(c)
        if (allowed) {
            usage = withContext(Dispatchers.IO) { readUsage(c) }
            hourly = withContext(Dispatchers.IO) { readHourlyUsage(c) }
            withContext(Dispatchers.IO) { checkLimitAlerts(c, usage); DopaLessWidget.update(c) }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            allowed = hasUsageAccess(c)
            if (allowed) {
                usage = withContext(Dispatchers.IO) { readUsage(c) }
                hourly = withContext(Dispatchers.IO) { readHourlyUsage(c) }
                withContext(Dispatchers.IO) { checkLimitAlerts(c, usage); DopaLessWidget.update(c) }
            }
        }
    }

    val light = lightColorScheme(primary = Color(0xFF54745F), onPrimary = Color.White, background = Color(0xFFF5F6F2), surface = Color.White, onSurface = Color(0xFF202820), onSurfaceVariant = Color(0xFF727B73), surfaceVariant = Color(0xFFE8ECE6), outline = Color(0xFFDCE2DA))
    val night = darkColorScheme(primary = Color(0xFFA8C4AD), onPrimary = Color(0xFF1D3324), background = Color(0xFF141A16), surface = Color(0xFF1D251F), onSurface = Color(0xFFE7ECE7), onSurfaceVariant = Color(0xFFADB8AE), surfaceVariant = Color(0xFF303A32), outline = Color(0xFF465349))
    MaterialTheme(colorScheme = if (dark) night else light) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface), title = { Text("DopaLess", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { dark = !dark; p.edit().putBoolean("dark", dark).apply(); DopaLessWidget.update(c) }) { Icon(Icons.Rounded.DarkMode, "Theme") } }) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val labels = listOf("Home", "Usage", "Focus", "Challenges", "Growth", "Settings")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.BarChart, Icons.Rounded.CenterFocusStrong, Icons.Rounded.Flag, Icons.Rounded.Spa, Icons.Rounded.Settings)
                    labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i; tick++ }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant, indicatorColor = MaterialTheme.colorScheme.secondaryContainer), icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) }) }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(goal, { goal = it; p.edit().putInt("goal", it).apply(); DopaLessWidget.update(c) }, allowed, usage.total, hourly, { tab = 1 }, { c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
                    1 -> UsageScreen(allowed, usage, goal, limits, { c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, { tick++ }, { pkg, mins -> putLimit(c, pkg, mins); limits = getLimits(c); checkLimitAlerts(c, usage); DopaLessWidget.update(c) })
                    2 -> FocusScreen(p)
                    3 -> ChallengesScreen(p)
                    4 -> GrowthScreen(p, hourly, usage.total)
                    else -> SettingsScreen(dark) { dark = it; p.edit().putBoolean("dark", it).apply(); DopaLessWidget.update(c) }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(goal: Int, setGoal: (Int) -> Unit, allowed: Boolean, total: Long, hourly: List<Long>, openUsage: () -> Unit, enable: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Today", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCREEN TIME", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { if (allowed) (total / 60000f / goal).coerceIn(0f, 1f) else 0f }, modifier = Modifier.size(136.dp), strokeWidth = 9.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (allowed) fmt(total) else "--", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Text("today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if (!allowed) "Usage access is required" else if (total < goal * 60000L) fmt(goal * 60000L - total) + " remaining" else "Daily goal reached", fontWeight = FontWeight.Medium)
                    if (!allowed) Button(onClick = enable) { Text("Enable usage access") } else TextButton(onClick = openUsage) { Text("View app usage") }
                    Text("Daily goal · " + goal + " minutes", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hourly usage", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("When you used your phone more or less today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    val peak = (hourly.maxOrNull() ?: 0L).coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        hourly.forEach { value ->
                            val barHeight = (value.toFloat() / peak * 72f).coerceAtLeast(if (value > 0L) 5f else 2f)
                            Box(Modifier.weight(1f).height(barHeight.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0L) 0.85f else 0.12f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("12 AM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("6 AM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("12 PM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("6 PM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("12 AM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Daily goal", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { setGoal(240) }) { Text("Reset") }
            }
            Slider(value = goal.toFloat(), onValueChange = { setGoal(it.toInt()) }, valueRange = 30f..600f, steps = 18)
        }
    }
}

@Composable
private fun UsageScreen(allowed: Boolean, usage: UsageSnapshot, goal: Int, limits: Map<String, Int>, enable: () -> Unit, refresh: () -> Unit, save: (String, Int?) -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var selected by remember { mutableStateOf<AppUsage?>(null) }
    var minutes by remember { mutableFloatStateOf(60f) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Usage & app limits", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TODAY", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (allowed) fmt(usage.total) else "Not connected", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    if (allowed) {
                        LinearProgressIndicator(progress = { (usage.total / 60000f / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("Daily goal: " + goal + " minutes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        Text("Allow Usage Access in Android settings to read app activity.")
                        Button(onClick = enable) { Text("Open Usage Access settings") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("App time limits", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
            Text("Set a daily threshold for each app. DopaLess alerts you when it is reached.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                TextButton(onClick = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Enable limit notifications") }
            }
        }
        if (allowed && usage.apps.isEmpty()) item { Text("No app activity found yet. Refresh after using some apps.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(usage.apps, key = { it.packageName }) { app ->
            val limit = limits[app.packageName]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(fmt(app.millis), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        TextButton(onClick = { minutes = (limit ?: 60).toFloat(); selected = app }) { Text(if (limit == null) "Set limit" else limit.toString() + "m · Edit") }
                    }
                    if (limit != null) {
                        LinearProgressIndicator(progress = { (app.millis / 60000f / limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (app.millis >= limit * 60000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        if (app.millis >= limit * 60000L) Text("Daily limit reached", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Limit for " + app.label) },
            text = { Column {
                Text(minutes.toInt().toString() + " minutes per day")
                Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 15f..240f, steps = 14)
                Text("For automatic blocking, enable the DopaLess accessibility service in Settings. Usage access is also required.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton(onClick = { save(app.packageName, minutes.toInt()); selected = null }) { Text("Save") } },
            dismissButton = { Row {
                if (limits.containsKey(app.packageName)) TextButton(onClick = { save(app.packageName, null); selected = null }) { Text("Remove") }
                TextButton(onClick = { selected = null }) { Text("Cancel") }
            } }
        )
    }
}

@Composable
private fun FocusScreen(data: android.content.SharedPreferences) {
    val context = LocalContext.current
    var preset by remember { mutableIntStateOf(25) }
    var active by remember { mutableStateOf(data.getBoolean("timer_active", false)) }
    var timerTitle by remember { mutableStateOf(data.getString("timer_title", "Timer") ?: "Timer") }
    var paused by remember { mutableStateOf(data.getBoolean("timer_paused", false)) }
    var remaining by remember { mutableLongStateOf(0L) }
    var done by remember { mutableIntStateOf(data.getInt("focus_done", 0)) }
    LaunchedEffect(Unit) {
        while (true) {
            active = data.getBoolean("timer_active", false)
            timerTitle = data.getString("timer_title", "Timer") ?: "Timer"
            paused = data.getBoolean("timer_paused", false)
            val end = data.getLong("timer_end", 0L)
            remaining = if (paused) data.getLong("timer_remaining", 0L) else (end - System.currentTimeMillis()).coerceAtLeast(0L)
            done = data.getInt("focus_done", 0)
            delay(1_000L)
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Focus", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (active) Text(timerTitle, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(28.dp))
        Text(if (active) "%02d:%02d".format(remaining / 60000L, (remaining / 1000L) % 60L) else "%02d:00".format(preset), fontSize = 56.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(20.dp))
        if (!active) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15, 25, 45, 60, 90).forEach { FilterChip(selected = preset == it, onClick = { preset = it }, label = { Text(it.toString() + "m") }) } }
            Text("Custom duration · " + preset + " minutes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Slider(value = preset.toFloat(), onValueChange = { preset = it.toInt() }, valueRange = 5f..180f, steps = 34)
            Button(onClick = { TimerService.start(context, "Focus session", "focus", preset * 60_000L) }) { Text("Start") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { TimerService.command(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE) }) { Text(if (paused) "Resume" else "Pause") }
                OutlinedButton(onClick = { TimerService.command(context, TimerService.ACTION_STOP) }) { Text("End") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(done.toString() + " completed sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (active) Text("Timer continues when the app is closed", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:" + context.packageName)))
                }) { Text("Allow precise timer alerts") }
            }
        }
    }
}

private data class Challenge(val title: String, val mins: Int, val detail: String)

@Composable
private fun ChallengesScreen(data: android.content.SharedPreferences) {
    val context = LocalContext.current
    val challenges = remember { listOf(
        Challenge("30-minute break", 30, "Take a break from optional screen use"),
        Challenge("One-hour reset", 60, "Spend an hour on an offline activity"),
        Challenge("Focused study", 25, "Complete one focused work block"),
        Challenge("Screen-free meal", 30, "Keep one meal free from scrolling"),
        Challenge("Morning offline", 60, "Start the day away from feeds"),
        Challenge("Evening wind-down", 45, "Finish the day with an offline activity"),
        Challenge("Weekend reset", 120, "Make time for an offline hobby")
    ) }
    var active by remember { mutableIntStateOf(data.getInt("active_challenge_index", -1)) }
    var anyTimerActive by remember { mutableStateOf(data.getBoolean("timer_active", false)) }
    var running by remember { mutableStateOf(data.getBoolean("timer_active", false) && data.getString("timer_kind", "") == "challenge") }
    var paused by remember { mutableStateOf(data.getBoolean("timer_paused", false)) }
    var remaining by remember { mutableLongStateOf(0L) }
    var completed by remember { mutableIntStateOf(data.getInt("challenge_done", 0)) }
    LaunchedEffect(Unit) {
        while (true) {
            active = data.getInt("active_challenge_index", -1)
            anyTimerActive = data.getBoolean("timer_active", false)
            running = anyTimerActive && data.getString("timer_kind", "") == "challenge"
            paused = data.getBoolean("timer_paused", false)
            val end = data.getLong("timer_end", 0L)
            remaining = if (paused) data.getLong("timer_remaining", 0L) else (end - System.currentTimeMillis()).coerceAtLeast(0L)
            completed = data.getInt("challenge_done", 0)
            delay(500L)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Challenges", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
        item { Text(completed.toString() + " completed", color = MaterialTheme.colorScheme.primary) }
        if (anyTimerActive && !running) item { Text("Finish the active focus timer before starting a challenge.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        if (running && active in challenges.indices) item {
            val current = challenges[active]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IN PROGRESS", fontSize = 10.sp)
                    Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("%02d:%02d remaining".format(remaining / 60000L, (remaining / 1000L) % 60L))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { TimerService.command(context, if (paused) TimerService.ACTION_RESUME else TimerService.ACTION_PAUSE) }) { Text(if (paused) "Resume" else "Pause") }
                        OutlinedButton(onClick = { TimerService.command(context, TimerService.ACTION_STOP) }) { Text("Cancel") }
                    }
                }
            }
        }
        items(challenges.size) { i ->
            val challenge = challenges[i]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(challenge.title, fontWeight = FontWeight.Medium)
                        Text(challenge.mins.toString() + " min · " + challenge.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (running && active == i) Text("Active", color = MaterialTheme.colorScheme.primary)
                    else if (!anyTimerActive && active < 0) TextButton(onClick = {
                        data.edit().putInt("active_challenge_index", i).putLong("active_challenge_started", System.currentTimeMillis()).apply()
                        TimerService.start(context, challenge.title, "challenge", challenge.mins * 60_000L)
                    }) { Text("Start") }
                    else Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, setDark: (Boolean) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("App limits can automatically redirect you when their daily threshold is reached.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Enable app blocking") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", Modifier.weight(1f))
            Switch(checked = dark, onCheckedChange = setDark)
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:" + context.packageName)))
        }) { Text("Battery and app settings") }
        Text("On Xiaomi / MIUI, set DopaLess battery use to No restrictions and enable Autostart so timers are less likely to be stopped.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text("Version 0.6.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}
