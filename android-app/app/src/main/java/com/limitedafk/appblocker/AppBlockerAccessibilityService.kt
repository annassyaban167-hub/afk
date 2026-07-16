package com.limitedafk.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.limitedafk.appblocker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Skip checking self or home launcher packages to prevent lock loops
            if (packageName == this.packageName) return

            serviceScope.launch {
                val app = database.blockedAppDao().getApp(packageName)
                if (app != null) {
                    if (app.isBlocked || app.mode == "CLOSE_IMMEDIATELY" || app.remainingSeconds <= 0) {
                        blockCurrentApp()
                    }
                }
            }
        }
    }

    private fun blockCurrentApp() {
        // Force navigate home to dismiss the blocked app
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Broadcast lock status or launch alert overlays
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_TRIGGERED", true)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}
}
