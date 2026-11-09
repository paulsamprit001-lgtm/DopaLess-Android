package com.dopaless.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class AppLimitBlockActivity : Activity() {
    private var targetPackage: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPackage, 0)).toString() } catch (_: Exception) { "This app" }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Daily limit reached", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(label + " has reached the daily time limit you set.")
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { finishAndHome() }) { Text("Close app") }
                        TextButton(onClick = { grantExtra(5) }) { Text("Use 5 extra minutes") }
                        TextButton(onClick = { grantExtra(10) }) { Text("Use 10 extra minutes") }
                    }
                }
            }
        }
    }
    private fun grantExtra(minutes: Int) {
        getSharedPreferences("dopaless_preferences", MODE_PRIVATE).edit()
            .putLong("extra_" + targetPackage, System.currentTimeMillis() + minutes * 60_000L)
            .remove("blocked_package").apply()
        val launch = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launch != null) startActivity(launch)
        finish()
    }
    private fun finishAndHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
    companion object { const val EXTRA_PACKAGE = "target_package" }
}
