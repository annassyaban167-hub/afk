package com.limitedafk.appblocker.data

data class BlockedApp(
    val packageName: String,
    val appName: String,
    val limitMinutes: Int,
    var remainingSeconds: Int,
    val mode: String,
    var isBlocked: Boolean = false
)
