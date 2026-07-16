package com.limitedafk.appblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockedAppTest {
    @Test
    fun testBlockedAppCreation() {
        val app = BlockedApp("com.whatsapp", "WhatsApp", 30, 1800, "CLOSE_AFTER_TIMEOUT")
        assertEquals("com.whatsapp", app.packageName)
        assertEquals("WhatsApp", app.appName)
        assertEquals(30, app.limitMinutes)
        assertEquals(1800, app.remainingSeconds)
        assertEquals("CLOSE_AFTER_TIMEOUT", app.mode)
        assertEquals(false, app.isBlocked)
    }
}
