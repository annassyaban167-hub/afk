package com.limitedafk.appblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BlockedAppTest {
    @Test
    fun appCreation_setsAllFields() {
        val app = BlockedApp(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            limitMinutes = 30,
            remainingSeconds = 1800,
            mode = "CLOSE_AFTER_TIMEOUT"
        )
        assertEquals("com.whatsapp", app.packageName)
        assertEquals("WhatsApp", app.appName)
        assertEquals(30, app.limitMinutes)
        assertEquals(1800, app.remainingSeconds)
        assertEquals("CLOSE_AFTER_TIMEOUT", app.mode)
        assertFalse(app.isBlocked)
    }

    @Test
    fun appCreation_closeImmediateMode() {
        val app = BlockedApp(
            packageName = "com.instagram",
            appName = "Instagram",
            limitMinutes = 0,
            remainingSeconds = 0,
            mode = "CLOSE_IMMEDIATELY"
        )
        assertEquals("CLOSE_IMMEDIATELY", app.mode)
        assertEquals(0, app.limitMinutes)
    }
}
