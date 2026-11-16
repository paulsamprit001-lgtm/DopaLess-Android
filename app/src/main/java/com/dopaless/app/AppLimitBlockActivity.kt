package com.dopaless.app

import androidx.activity.ComponentActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AppLimitBlockActivity : ComponentActivity() {
    private var targetPackage: String = ""
    private var shortFeed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        shortFeed = intent.getBooleanExtra(EXTRA_SHORT_FEED, false)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishAndHome() }
        })
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString()
        } catch (_: Exception) { "This app" }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (shortFeed) {
                            Text("Scroll Shield", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text("Short-form feed interrupted", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("You can still use the rest of $label. This pause is a chance to choose what you wanted to do.")
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { grantCuriousPass() }) { Text("Curious Mode · 60 seconds") }
                            val prefs = getSharedPreferences("dopaless_preferences", MODE_PRIVATE)
                            val cooldown = prefs.getLong("short_pause_cooldown_until", 0L)
                            if (cooldown <= System.currentTimeMillis()) {
                                TextButton(onClick = { pauseShieldBriefly() }) { Text("Pause protection for 5 minutes") }
                            } else {
                                Text("A short pause is available later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { finishAndHome() }) { Text("Close and return home") }
                        } else {
                            Text("Daily limit reached", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(12.dp))
                            Text("$label has reached the daily time limit you set.")
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { finishAndHome() }) { Text("Close app") }
                            TextButton(onClick = { grantExtra(5) }) { Text("Use 5 extra minutes") }
                            TextButton(onClick = { grantExtra(10) }) { Text("Use 10 extra minutes") }
                        }
                    }
                }
            }
        }
    }

    private fun launchTarget() {
        val launch = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launch != null) startActivity(launch) else finishAndHome()
        finish()
    }

    private fun grantCuriousPass() {
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE).edit()
            .putLong("short_allow_until_$targetPackage", System.currentTimeMillis() + 60_000L)
            .apply()
        launchTarget()
    }

    private fun pauseShieldBriefly() {
        val now = System.currentTimeMillis()
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE).edit()
            .putLong("short_pause_until", now + 5 * 60_000L)
            .putLong("short_pause_cooldown_until", now + 60 * 60_000L)
            .apply()
        launchTarget()
    }

    private fun grantExtra(minutes: Int) {
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE).edit()
            .putLong("extra_" + targetPackage, System.currentTimeMillis() + minutes * 60_000L)
            .remove("blocked_package").apply()
        launchTarget()
    }

    private fun finishAndHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
        const val EXTRA_SHORT_FEED = "short_feed"
    }
}
