package com.limitedafk.appblocker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecurityUtilsTest {
    @Test
    fun testPinHashing() {
        val pin = "123456"
        val hash1 = SecurityUtils.hashPin(pin)
        val hash2 = SecurityUtils.hashPin(pin)
        assertEquals(hash1, hash2)

        val badPin = "654321"
        val badHash = SecurityUtils.hashPin(badPin)
        assertNotEquals(hash1, badHash)
    }
}
