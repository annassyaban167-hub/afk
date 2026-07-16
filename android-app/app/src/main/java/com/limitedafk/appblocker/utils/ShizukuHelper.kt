package com.limitedafk.appblocker.utils

import android.content.Context
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import com.limitedafk.appblocker.DeviceAdminReceiver

object ShizukuHelper {
    var isShizukuRunning = false

    fun buildForceStopCommand(packageName: String): String =
        "am force-stop $packageName"

    fun forceStopWithShizuku(packageName: String) {
        // placeholder — real Shizuku API call via content provider or process
        ProcessBuilder(listOf("sh", "-c", buildForceStopCommand(packageName)))
            .start()
    }

    fun forceStopWithDPM(context: Context, packageName: String): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, DeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dpm.setUninstallBlocked(admin, packageName, true)
                    dpm.setUninstallBlocked(admin, packageName, false)
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
