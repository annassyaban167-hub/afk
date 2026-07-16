package com.limitedafk.appblocker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {
    @Test
    fun hashPin_isDeterministic() {
        val h1 = SecurityUtils.hashPin("123456")
        val h2 = SecurityUtils.hashPin("123456")
        assertEquals(h1, h2)
    }

    @Test
    fun hashPin_differentPinsDiffer() {
        val h1 = SecurityUtils.hashPin("123456")
        val h2 = SecurityUtils.hashPin("654321")
        assertNotEquals(h1, h2)
    }

    @Test
    fun hashPin_returns64HexChars() {
        val hash = SecurityUtils.hashPin("000000")
        assertTrue(hash.length == 64)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
