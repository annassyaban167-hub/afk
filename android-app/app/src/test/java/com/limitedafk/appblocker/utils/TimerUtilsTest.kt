package com.limitedafk.appblocker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerUtilsTest {

    @Test
    fun calculateRemaining_subtractsElapsedFromTotal() {
        assertEquals(50, TimerUtils.calculateRemaining(100, 50))
        assertEquals(0, TimerUtils.calculateRemaining(30, 60))
        assertEquals(300, TimerUtils.calculateRemaining(300, 0))
    }

    @Test
    fun calculateRemaining_neverReturnsNegative() {
        assertEquals(0, TimerUtils.calculateRemaining(10, 20))
        assertEquals(0, TimerUtils.calculateRemaining(0, 1))
    }

    @Test
    fun isExpired_zeroOrNegativeIsExpired() {
        assertTrue(TimerUtils.isExpired(0))
        assertTrue(TimerUtils.isExpired(-5))
    }

    @Test
    fun isExpired_positiveIsNotExpired() {
        assertFalse(TimerUtils.isExpired(1))
        assertFalse(TimerUtils.isExpired(60))
    }

    @Test
    fun formatTime_returnsMmSs() {
        assertEquals("05:30", TimerUtils.formatTime(330))
        assertEquals("01:00", TimerUtils.formatTime(60))
        assertEquals("00:00", TimerUtils.formatTime(0))
        assertEquals("00:01", TimerUtils.formatTime(1))
    }

    @Test
    fun formatTime_handlesHours() {
        assertEquals("90:00", TimerUtils.formatTime(5400))
    }
}
