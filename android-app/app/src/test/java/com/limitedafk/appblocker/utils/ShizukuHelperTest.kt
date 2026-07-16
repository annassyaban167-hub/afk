package com.limitedafk.appblocker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuHelperTest {

    @Test
    fun buildForceStopCommand_returnsValidShellCommand() {
        val cmd = ShizukuHelper.buildForceStopCommand("com.whatsapp")
        assertEquals("am force-stop com.whatsapp", cmd)
    }

    @Test
    fun buildForceStopCommand_includesPackageName() {
        val pkg = "com.example.app"
        val cmd = ShizukuHelper.buildForceStopCommand(pkg)
        assertTrue(cmd.contains(pkg))
        assertTrue(cmd.startsWith("am force-stop"))
    }

    @Test
    fun isPermissionGranted_defaultsToFalse() {
        // Without Shizuku running, this should be false
        assertFalse(ShizukuHelper.isShizukuRunning)
    }
}
