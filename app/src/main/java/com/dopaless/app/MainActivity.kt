package com.dopaless.app

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Green = Color(0xFF557C65)
private val Muted = Color(0xFF7D8580)
private val Paper = Color(0xFFF6F7F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DopaLessApp() }
    }
}

@Composable
private fun DopaLessApp() {
    var tab by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(false) }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5A9)) else lightColorScheme(primary = Green, background = Paper)
    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { TopAppBar(title = { Column { Text("DopaLess", fontWeight = FontWeight.Bold); Text("A little more intention.", fontSize = 11.sp, color = Muted) } }, actions = { IconButton(onClick = { dark = !dark }) { Icon(Icons.Rounded.DarkMode, "Toggle theme") } }) },
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Usage", "Focus", "Challenges", "Profile").forEachIndexed { i, label ->
                        val icons = listOf(Icons.Rounded.Home, Icons.Rounded.BarChart, Icons.Rounded.CenterFocusStrong, Icons.Rounded.Flag, Icons.Rounded.Person)
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> SimpleScreen("Your usage", "Usage access is optional. Enable it in Android Settings when usage summaries are added.")
                    2 -> FocusScreen()
                    3 -> ChallengesScreen()
                    else -> Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your space", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("Privacy-first settings", color = Muted)
                        Text("Theme: " + if (dark) "Dark" else "Light")
                        OutlinedButton(onClick = { dark = !dark }) { Text("Switch theme") }
                        Text("Your data stays on this device.", color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    var goal by remember { mutableIntStateOf(240) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Today", fontSize = 26.sp, fontWeight = FontWeight.SemiBold); Text("Make room for what matters.", color = Muted) }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAILY SCREEN-TIME GOAL", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(progress = { 0.0f }, modifier = Modifier.size(132.dp), strokeWidth = 9.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Usage tracking not connected", fontWeight = FontWeight.Medium)
                    Text("Enable Android Usage Access when you want real statistics.", color = Muted, fontSize = 12.sp)
                    Text("Goal: ${goal / 60}h ${goal % 60}m", color = Green)
                }
            }
        }
        item { Text("A gentle reminder", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        item { Text("Use your phone when you need it. Stop when you don't.", color = Muted) }
        item { Text("Daily goal (minutes)") }
        item { Slider(value = goal.toFloat(), onValueChange = { goal = it.toInt() }, valueRange = 60f..600f) }
    }
}

@Composable
private fun FocusScreen() {
    var duration by remember { mutableIntStateOf(25) }
    var seconds by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    LaunchedEffect(running, seconds) {
        if (running && seconds > 0) { delay(1000); seconds--; if (seconds == 0) { running = false; done++ } }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Focus mode", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("One thing at a time.", color = Muted)
        Spacer(Modifier.height(30.dp))
        Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 54.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(20.dp))
        if (seconds == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15,25,45,60).forEach { FilterChip(selected = duration == it, onClick = { duration = it }, label = { Text("${it}m") }) } }
            Button(onClick = { seconds = duration * 60; running = true }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Start focus") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { running = !running }) { Text(if (running) "Pause" else "Resume") }
                OutlinedButton(onClick = { running = false; seconds = 0 }) { Text("End") }
            }
        }
        Spacer(Modifier.height(16.dp)); Text("Completed sessions: $done", color = Muted)
    }
}

@Composable
private fun ChallengesScreen() {
    val items = listOf("30-minute no-social-media", "1-hour intentional phone use", "2-hour distraction-free", "Morning without scrolling", "No-phone study session", "Digital sunset", "Weekend digital balance")
    var active by remember { mutableStateOf("") }
    var completed by remember { mutableIntStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Detox challenges", fontSize = 25.sp, fontWeight = FontWeight.SemiBold); Text("Small steps, no pressure.", color = Muted) }
        item { Text("Completed: $completed", color = Green) }
        items(items.size) { i ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(items[i], Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    TextButton(onClick = { if (active == items[i]) { completed++; active = "" } else if (active.isEmpty()) active = items[i] }) { Text(if (active == items[i]) "Complete" else if (active.isEmpty()) "Start" else "Later") }
                }
            }
        }
    }
}

@Composable
private fun SimpleScreen(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted)
    }
}
