package com.limitedafk.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.limitedafk.appblocker.data.AppDatabase
import com.limitedafk.appblocker.utils.ShizukuHelper
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
            if (packageName == this.packageName) return

            serviceScope.launch {
                val app = database.blockedAppDao().getApp(packageName)
                if (app != null && app.enabled) {
                    if (app.mode == "CLOSE_IMMEDIATELY" || (app.mode == "CLOSE_AFTER_TIMEOUT" && app.remainingSeconds <= 0)) {
                        // Try Shizuku first, fallback to DPM
                        if (ShizukuHelper.isShizukuRunning) {
                            ShizukuHelper.forceStopWithShizuku(packageName)
                        } else {
                            ShizukuHelper.forceStopWithDPM(this@AppBlockerAccessibilityService, packageName)
                        }
                        performGlobalAction(GLOBAL_ACTION_HOME)

                        val intent = Intent(this@AppBlockerAccessibilityService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("BLOCKED_TRIGGERED", true)
                            putExtra("PACKAGE_BLOCKED", packageName)
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}
}
