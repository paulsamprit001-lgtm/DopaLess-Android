package com.dopaless.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val Green = Color(0xFF557C65)
private val Muted = Color(0xFF7D8580)
private val Paper = Color(0xFFF6F7F5)
private const val PREFS = "dopaless_preferences"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DopaLessApp() }
    }
}

private fun store(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private fun usageAllowed(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

private fun readTodayUsage(context: Context): Long {
    if (!usageAllowed(context)) return 0L
    val start = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    return manager.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values.sumOf { it.totalTimeInForeground }
}

private fun duration(ms: Long): String {
    val mins = (ms / 60_000L).toInt()
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DopaLessApp() {
    val context = LocalContext.current
    val data = remember { store(context) }
    var tab by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(data.getBoolean("dark", false)) }
    var goal by remember { mutableIntStateOf(data.getInt("goal", 240)) }
    var usage by remember { mutableLongStateOf(0L) }
    var allowed by remember { mutableStateOf(usageAllowed(context)) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, refresh) {
        if (tab == 0 || tab == 1) {
            allowed = usageAllowed(context)
            if (allowed) usage = readTodayUsage(context)
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5A9)) else lightColorScheme(primary = Green, background = Paper)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Column { Text("DopaLess", fontWeight = FontWeight.Bold); Text("A little more intention.", fontSize = 11.sp, color = Muted) } },
                    actions = { IconButton(onClick = { dark = !dark; data.edit().putBoolean("dark", dark).apply() }) { Icon(Icons.Rounded.DarkMode, "Toggle theme") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    val labels = listOf("Home", "Usage", "Focus", "Challenges", "Profile")
                    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.BarChart, Icons.Rounded.CenterFocusStrong, Icons.Rounded.Flag, Icons.Rounded.Person)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i; if (i < 2) refresh++ }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(goal, { goal = it; data.edit().putInt("goal", it).apply() }, allowed, usage) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    1 -> UsageScreen(allowed, usage, goal, { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { refresh++ }
                    2 -> FocusScreen(data)
                    3 -> ChallengesScreen(data)
                    else -> ProfileScreen(dark) { dark = it; data.edit().putBoolean("dark", it).apply() }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(goal: Int, setGoal: (Int) -> Unit, allowed: Boolean, usage: Long, enable: () -> Unit) {
    val progress = if (allowed) (usage / 60_000f / goal).coerceIn(0f, 1f) else 0f
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Today", fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("Make room for what matters.", color = Muted) }
        item {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAILY SCREEN-TIME GOAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(132.dp), strokeWidth = 9.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (allowed) duration(usage) else "--", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                            Text("today", fontSize = 11.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if (!allowed) "Usage tracking is optional" else {
                        val left = (goal * 60_000L - usage).coerceAtLeast(0L)
                        if (left > 0) "${duration(left)} left in your goal" else "Daily goal reached"
                    }, fontWeight = FontWeight.Medium)
                    if (!allowed) {
                        Text("Allow Usage Access to see today's total on this device.", color = Muted, fontSize = 12.sp)
                        TextButton(onClick = enable) { Text("Enable usage access") }
                    }
                    Text("Goal: ${goal / 60}h ${goal % 60}m", color = Green)
                }
            }
        }
        item { Text("A gentle reminder", fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Text("Use your phone when you need it. Stop when you don't.", color = Muted) }
        item { Text("Daily goal (minutes)") }
        item { Slider(value = goal.toFloat(), onValueChange = setGoal, valueRange = 60f..600f, steps = 17) }
    }
}

@Composable
private fun UsageScreen(allowed: Boolean, usage: Long, goal: Int, enable: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    var refreshCount by remember { mutableIntStateOf(0) }
    val total = remember(allowed, usage, refreshCount) { if (allowed) readTodayUsage(context) else 0L }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your usage", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("A simple view of your device activity today.", color = Muted)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TODAY'S SCREEN TIME", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(if (allowed) duration(total) else "Not connected", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                if (allowed) {
                    LinearProgressIndicator(progress = { (total / 60_000f / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("Daily goal: $goal minutes", color = Muted, fontSize = 12.sp)
                    Text("Android supplies these usage estimates.", color = Muted, fontSize = 11.sp)
                } else {
                    Text("No usage data is read until you grant Android Usage Access.", color = Muted)
                    Button(onClick = enable) { Text("Open Usage Access settings") }
                }
            }
        }
        if (allowed) OutlinedButton(onClick = { refreshCount++; refresh() }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh") }
        Text("Privacy note", fontWeight = FontWeight.SemiBold)
        Text("Usage data is read locally on your device and is not sent to a server.", color = Muted)
    }
}

@Composable
private fun FocusScreen(data: android.content.SharedPreferences) {
    var durationMins by remember { mutableIntStateOf(25) }
    var seconds by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var endAt by remember { mutableLongStateOf(0L) }
    var done by remember { mutableIntStateOf(data.getInt("focus_done", 0)) }

    LaunchedEffect(running) {
        while (running) {
            val left = ((endAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
            seconds = left
            if (left == 0) {
                running = false
                done++
                data.edit().putInt("focus_done", done).apply()
            } else delay(500L)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Focus mode", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("One thing at a time.", color = Muted)
        Spacer(Modifier.height(30.dp))
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 54.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(20.dp))
        if (seconds == 0 && !running) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 25, 45, 60).forEach { FilterChip(selected = durationMins == it, onClick = { durationMins = it }, label = { Text("${it}m") }) }
            }
            Button(onClick = { seconds = durationMins * 60; endAt = System.currentTimeMillis() + seconds * 1000L; running = true }) { Text("Start focus") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (running) {
                        seconds = ((endAt - System.currentTimeMillis() + 999L) / 1000L).toInt().coerceAtLeast(0)
                        running = false
                    } else {
                        endAt = System.currentTimeMillis() + seconds * 1000L
                        running = true
                    }
                }) { Text(if (running) "Pause" else "Resume") }
                OutlinedButton(onClick = { running = false; seconds = 0 }) { Text("End") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Completed sessions: $done", color = Muted)
    }
}

@Composable
private fun ChallengesScreen(data: android.content.SharedPreferences) {
    val challenges = listOf("30-minute social break", "One hour of intentional use", "A distraction-free study block", "A calm morning before scrolling", "A screen-free meal", "A gentle digital sunset", "A balanced weekend")
    var active by remember { mutableStateOf(data.getString("active_challenge", "") ?: "") }
    var completed by remember { mutableIntStateOf(data.getInt("challenge_done", 0)) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Detox challenges", fontSize = 25.sp, fontWeight = FontWeight.SemiBold); Text("Small steps, no pressure.", color = Muted) }
        item { Text("Completed: $completed", color = Green) }
        items(challenges.size) { index ->
            val challenge = challenges[index]
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(challenge, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    TextButton(onClick = {
                        if (active == challenge) {
                            completed++
                            active = ""
                            data.edit().putInt("challenge_done", completed).remove("active_challenge").apply()
                        } else if (active.isEmpty()) {
                            active = challenge
                            data.edit().putString("active_challenge", active).apply()
                        }
                    }) { Text(if (active == challenge) "Complete" else if (active.isEmpty()) "Start" else "Later") }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(dark: Boolean, setDark: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Your space", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text("Simple settings, stored on this device.", color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", Modifier.weight(1f))
            Switch(checked = dark, onCheckedChange = setDark)
        }
        Text("Privacy-first by design", fontWeight = FontWeight.SemiBold)
        Text("No account required. Screen-time access is optional and controlled by Android settings.", color = Muted)
        Text("Version 0.2.0", color = Muted, fontSize = 12.sp)
    }
}
