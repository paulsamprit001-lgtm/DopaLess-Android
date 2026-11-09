package com.dopaless.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GrowthScreen(data: SharedPreferences, hourly: List<Long>, dailyUsage: Long) {
    var journal by remember { mutableStateOf(data.getString("daily_journal", "") ?: "") }
    var trigger by remember { mutableStateOf(data.getString("diary_trigger", "") ?: "") }
    var sunset by remember { mutableStateOf(data.getBoolean("digital_sunset", false)) }
    var challenge by remember { mutableIntStateOf(data.getInt("journey_days", 3)) }
    var reality by remember { mutableStateOf(false) }
    var urge by remember { mutableStateOf(false) }
    val focus = data.getInt("focus_done", 0)
    val done = data.getInt("challenge_done", 0)
    val totalSessions = focus + done
    val weekMinutes = dailyUsage / 60000L * 7L
    val yearHours = dailyUsage / 3600000L * 365L
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Growth & reflection", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DIGITAL GARDEN", color = MaterialTheme.colorScheme.primary)
            Text(if (totalSessions == 0) "Your garden is ready to grow" else "Your garden is growing", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("✿  ".repeat(totalSessions.coerceAtMost(8)) + if (totalSessions == 0) "○" else "🌱", fontSize = 23.sp)
            Text(totalSessions.toString() + " completed focus sessions and challenges. Progress is never reset for a missed day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("LIFE PROGRESS CALCULATOR", color = MaterialTheme.colorScheme.primary)
            Text("If today's screen time stayed similar:")
            Text((weekMinutes / 60).toString() + " hours / week", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(yearHours.toString() + " hours / year", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Consider using part of that time for reading, learning or a hobby.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REALITY CHECK", color = MaterialTheme.colorScheme.primary)
            Text("Pause before opening a distracting app.")
            Button(onClick = { reality = true }) { Text("Take a pause") }
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ACHIEVEMENTS", color = MaterialTheme.colorScheme.primary)
            Text(if (focus > 0) "✓ First focus session" else "○ First focus session")
            Text(if (focus >= 5) "✓ Five focus sessions" else "○ Five focus sessions · " + focus + "/5")
            Text(if (done > 0) "✓ First challenge completed" else "○ First challenge completed")
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("URGE SURFING", color = MaterialTheme.colorScheme.primary)
            Text("Pause, notice the trigger, and choose your next action.")
            Button(onClick = { urge = true }) { Text("Start mindful pause") }
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DOPAMINE DIARY · DAILY REFLECTION", color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(trigger, { trigger = it; data.edit().putString("diary_trigger", it).apply() }, Modifier.fillMaxWidth(), label = { Text("What triggered an unplanned check?") }, minLines = 2)
            OutlinedTextField(journal, { journal = it; data.edit().putString("daily_journal", it).apply() }, Modifier.fillMaxWidth(), label = { Text("What went well today?") }, minLines = 3)
            Text("Saved locally on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("WEEKLY LIFE REPORT", color = MaterialTheme.colorScheme.primary)
            Text("Projected weekly screen time: " + (weekMinutes / 60) + "h " + (weekMinutes % 60) + "m")
            Text("Focus sessions: " + focus + " · Challenges completed: " + done)
            Text("Review your Usage tab to identify one practical improvement.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HABIT REPLACEMENT", color = MaterialTheme.colorScheme.primary)
            listOf("Read five pages", "Take a short walk", "Sketch or write for 10 minutes", "Practise an instrument", "Tidy one small space").forEach { Text("• " + it) }
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DIGITAL SUNSET", color = MaterialTheme.colorScheme.primary)
            Text("Choose an evening wind-down reminder.")
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Enable sunset reminder", Modifier.weight(1f)); Switch(sunset, { sunset = it; data.edit().putBoolean("digital_sunset", it).apply() }) }
            Text("Preference saved locally. Scheduled alerts are not yet configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DISTRACTION HEATMAP", color = MaterialTheme.colorScheme.primary)
            Text("Hourly phone activity today")
            val peak = (hourly.maxOrNull() ?: 1L).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                hourly.forEach { value -> Box(Modifier.weight(1f).height((8f + 48f * value / peak).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0) .9f else .15f), RoundedCornerShape(3.dp))) }
            }
            Text("12 AM                       6 AM                       12 PM                       6 PM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PERSONAL 3 / 7 / 30-DAY JOURNEY", color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(3, 7, 30).forEach { days -> FilterChip(challenge == days, { challenge = days; data.edit().putInt("journey_days", days).apply() }, label = { Text(days.toString() + " days") }) } }
            Text("Selected journey: " + challenge + " days. Build awareness first, then a sustainable routine.")
            Text("Progress: " + data.getInt("journey_progress", 0).coerceAtMost(challenge) + " / " + challenge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } } }
        item { Text("Offline mode: core tools and saved reflections work without internet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
    if (reality) AlertDialog(onDismissRequest = { reality = false }, title = { Text("Why am I opening this?") }, text = { Text("What did I intend to do? Is this the best use of the next few minutes? Could I return later?") }, confirmButton = { TextButton(onClick = { reality = false }) { Text("Continue mindfully") } }, dismissButton = { TextButton(onClick = { reality = false }) { Text("Not now") } })
    if (urge) AlertDialog(onDismissRequest = { urge = false }, title = { Text("Ride out the urge") }, text = { Text("Take three slow breaths. Name the feeling or trigger without judging it. Choose one next action: return to your task, take a short walk, or write down what you need.") }, confirmButton = { TextButton(onClick = { urge = false }) { Text("Done") } })
}
