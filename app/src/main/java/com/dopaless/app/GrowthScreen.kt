package com.dopaless.app

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var challenge by remember { mutableIntStateOf(data.getInt("journey_days", 3)) }
    var gardenOpen by remember { mutableStateOf(false) }
    val focus = data.getInt("focus_done", 0)
    val done = data.getInt("challenge_done", 0)
    val totalSessions = focus + done
    val weekMinutes = dailyUsage / 60000L * 7L
    val yearHours = dailyUsage / 3600000L * 365L

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Growth", fontSize = 25.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Card(Modifier.fillMaxWidth().clickable { gardenOpen = true }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DIGITAL GARDEN", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("Open  ›", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                    Text(if (totalSessions == 0) "Your garden is ready to grow" else "Your garden is growing", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("✿  ".repeat(totalSessions.coerceAtMost(8)) + if (totalSessions == 0) "○" else "🌱", fontSize = 23.sp)
                    Text("$totalSessions completed focus sessions and challenges", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LIFE PROGRESS CALCULATOR", color = MaterialTheme.colorScheme.primary)
                    Text("If today's screen time stayed similar:")
                    Text((weekMinutes / 60).toString() + " hours / week", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(yearHours.toString() + " hours / year", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DISTRACTION HEATMAP", color = MaterialTheme.colorScheme.primary)
                    Text("Hourly phone activity today")
                    val peak = (hourly.maxOrNull() ?: 1L).coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                        hourly.forEach { value ->
                            Box(Modifier.weight(1f).height((8f + 48f * value / peak).dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (value > 0) .9f else .15f), RoundedCornerShape(3.dp)))
                        }
                    }
                    Text("12 AM                       6 AM                       12 PM                       6 PM", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PERSONAL 3 / 7 / 30-DAY CHALLENGE", color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 30).forEach { days ->
                            FilterChip(challenge == days, { challenge = days; data.edit().putInt("journey_days", days).apply() }, label = { Text("$days days") })
                        }
                    }
                    Text("Selected challenge: $challenge days")
                    Text("Focus sessions: $focus · Challenges completed: $done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (gardenOpen) {
        AlertDialog(
            onDismissRequest = { gardenOpen = false },
            title = { Text("Your Digital Garden") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (totalSessions == 0) "Your first completed session will plant the first seed." else "Every completed session helps your garden grow.")
                    Text("Focus sessions: $focus")
                    Text("Challenges completed: $done")
                    Text("Total growth points: $totalSessions")
                    Text("Complete a focus timer or a challenge to grow your garden.")
                }
            },
            confirmButton = { TextButton(onClick = { gardenOpen = false }) { Text("Got it") } }
        )
    }
}
