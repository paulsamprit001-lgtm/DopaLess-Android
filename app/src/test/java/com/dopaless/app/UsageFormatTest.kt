package com.dopaless.app

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatTest {
    @Test fun formatsMinutes() {
        assertEquals("0m", fmt(0L))
        assertEquals("42m", fmt(42L * 60_000L))
    }

    @Test fun formatsHoursAndMinutes() {
        assertEquals("1h 0m", fmt(60L * 60_000L))
        assertEquals("2h 7m", fmt((127L * 60_000L)))
    }

    @Test fun negativeDurationsClampToZero() {
        assertEquals("0m", fmt(-10L))
    }
}
