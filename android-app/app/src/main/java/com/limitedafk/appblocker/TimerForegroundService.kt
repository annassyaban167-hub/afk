package com.limitedafk.appblocker

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.limitedafk.appblocker.data.AppDatabase
import kotlinx.coroutines.*
import java.util.*

class TimerForegroundService : Service() {

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var database: AppDatabase
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForeground(NOTIFICATION_ID, getNotification("Monitoring active"))
            startMonitoringLoop()
        }
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive) {
                val currentApp = getForegroundApp()
                if (currentApp != null) {
                    val app = database.blockedAppDao().getApp(currentApp)
                    if (app != null) {
                        if (app.remainingSeconds > 0 && app.mode == "CLOSE_AFTER_TIMEOUT") {
                            app.remainingSeconds -= 1
                            database.blockedAppDao().update(app)
                            if (app.remainingSeconds <= 0) {
                                app.isBlocked = true
                                database.blockedAppDao().update(app)
                                triggerBlockIntent(app.packageName)
                            }
                        }
                    }
                }
                delay(1000) // Poll foreground app and update every 1 second
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        if (stats != null && stats.isNotEmpty()) {
            val sorted = stats.sortedByDescending { it.lastTimeUsed }
            return sorted.firstOrNull()?.packageName
        }
        return null
    }

    private fun triggerBlockIntent(packageName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_TRIGGERED", true)
            putExtra("PACKAGE_BLOCKED", packageName)
        }
        startActivity(intent)
    }

    private fun getNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AFK App Blocker")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
