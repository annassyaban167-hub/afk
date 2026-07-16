package com.limitedafk.appblocker.utils

object TimerUtils {
    fun calculateRemaining(totalSeconds: Int, elapsedSeconds: Int): Int =
        (totalSeconds - elapsedSeconds).coerceAtLeast(0)

    fun isExpired(remainingSeconds: Int): Boolean = remainingSeconds <= 0

    fun formatTime(totalSeconds: Int): String {
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return "%02d:%02d".format(min, sec)
    }
}
