package com.dopaless.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.RemoteViews

private const val TIMER_PREFS = "dopaless_preferences"
private const val TIMER_CHANNEL = "dopaless_timer"
private const val TIMER_NOTIFICATION_ID = 4102

class TimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var title = "Focus timer"
    private var kind = "focus"
    private var paused = false
    private var remainingWhenPaused = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (paused) return
            val remaining = endAt - System.currentTimeMillis()
            if (remaining <= 0L) {
                finishTimer()
            } else {
                updateNotification()
                DopaLessWidget.update(this@TimerService)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                title = intent.getStringExtra(EXTRA_TITLE) ?: "Focus timer"
                kind = intent.getStringExtra(EXTRA_KIND) ?: "focus"
                val duration = intent.getLongExtra(EXTRA_DURATION, 25L * 60_000L).coerceAtLeast(1_000L)
                endAt = System.currentTimeMillis() + duration
                paused = false
                remainingWhenPaused = 0L
                saveState(true, duration)
                startForeground(TIMER_NOTIFICATION_ID, buildNotification(duration))
                DopaLessWidget.update(this)
                handler.removeCallbacks(ticker)
                handler.post(ticker)
            }
            ACTION_PAUSE -> {
                if (!paused && endAt > 0L) {
                    remainingWhenPaused = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                    paused = true
                    saveState(true, remainingWhenPaused)
                    handler.removeCallbacks(ticker)
                    updateNotification()
                    DopaLessWidget.update(this)
                }
            }
            ACTION_RESUME -> {
                if (paused && remainingWhenPaused > 0L) {
                    endAt = System.currentTimeMillis() + remainingWhenPaused
                    paused = false
                    saveState(true, remainingWhenPaused)
                    updateNotification()
                    DopaLessWidget.update(this)
                    handler.removeCallbacks(ticker)
                    handler.post(ticker)
                }
            }
            ACTION_STOP -> stopTimer()
            else -> {
                val p = getSharedPreferences(TIMER_PREFS, MODE_PRIVATE)
                if (p.getBoolean("timer_active", false)) {
                    title = p.getString("timer_title", "Focus timer") ?: "Focus timer"
                    kind = p.getString("timer_kind", "focus") ?: "focus"
                    paused = p.getBoolean("timer_paused", false)
                    remainingWhenPaused = p.getLong("timer_remaining", 0L)
                    endAt = p.getLong("timer_end", 0L)
                    if (!paused && endAt > System.currentTimeMillis()) {
                        startForeground(TIMER_NOTIFICATION_ID, buildNotification(endAt - System.currentTimeMillis()))
                        handler.post(ticker)
                    } else if (paused) {
                        startForeground(TIMER_NOTIFICATION_ID, buildNotification(remainingWhenPaused))
                    } else {
                        stopTimer()
                    }
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun saveState(active: Boolean, remaining: Long) {
        getSharedPreferences(TIMER_PREFS, MODE_PRIVATE).edit()
            .putBoolean("timer_active", active)
            .putBoolean("timer_paused", paused)
            .putString("timer_title", title)
            .putString("timer_kind", kind)
            .putLong("timer_end", endAt)
            .putLong("timer_remaining", remaining)
            .apply()
    }

    private fun buildNotification(remaining: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResume = servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, 11)
        val stop = servicePendingIntent(ACTION_STOP, 12)
        val builder = Notification.Builder(this, TIMER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(if (paused) "Paused · " + formatTime(remaining) else "Time remaining · " + formatTime(remaining))
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, if (paused) "Resume" else "Pause", pauseResume).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
        if (!paused && Build.VERSION.SDK_INT >= 24) {
            builder.setWhen(System.currentTimeMillis() + remaining)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        return builder.build()
    }

    private fun updateNotification() {
        val remaining = if (paused) remainingWhenPaused else (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(TIMER_NOTIFICATION_ID, buildNotification(remaining))
    }

    private fun servicePendingIntent(action: String, request: Int): PendingIntent {
        val i = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(this, request, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun finishTimer() {
        handler.removeCallbacks(ticker)
        saveState(false, 0L)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val complete = Notification.Builder(this, TIMER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer finished")
            .setContentText(title + " is complete")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(TIMER_NOTIFICATION_ID + 1, complete)
        DopaLessWidget.update(this)
        playGentleChime()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playGentleChime() {
        Thread {
            try {
                val sampleRate = 22050
                val durationSeconds = 8
                val count = sampleRate * durationSeconds
                val samples = ShortArray(count)
                for (i in samples.indices) {
                    val fade = minOf(1f, i / 500f, (count - i) / 3000f).coerceAtLeast(0f)
                    val envelope = (0.11f * fade)
                    samples[i] = (kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * Short.MAX_VALUE * envelope).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.setVolume(0.22f)
                track.play()
                Thread.sleep(durationSeconds * 1000L)
                track.stop()
                track.release()
            } catch (_: Exception) { }
        }.start()
    }

    private fun stopTimer() {
        handler.removeCallbacks(ticker)
        saveState(false, 0L)
        DopaLessWidget.update(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(TIMER_CHANNEL, "Active timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows active focus and challenge timers"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        const val ACTION_START = "com.dopaless.action.START_TIMER"
        const val ACTION_PAUSE = "com.dopaless.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.dopaless.action.RESUME_TIMER"
        const val ACTION_STOP = "com.dopaless.action.STOP_TIMER"
        const val EXTRA_TITLE = "timer_title"
        const val EXTRA_KIND = "timer_kind"
        const val EXTRA_DURATION = "timer_duration"

        fun start(context: Context, title: String, kind: String, durationMillis: Long) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_DURATION, durationMillis)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun command(context: Context, action: String) {
            val intent = Intent(context, TimerService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= 26 && action != ACTION_STOP && action != ACTION_PAUSE) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}
