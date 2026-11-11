package com.example.reelbox.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun formatsMinutesAndSeconds() = assertEquals("1:05", formatTime(65_000))
    @Test fun formatsHours() = assertEquals("1:00:01", formatTime(3_601_000))
    @Test fun clampsNegative() = assertEquals("0:00", formatTime(-5))
    @Test fun shortDuration() {
        assertEquals("45s", formatDurationShort(45_000))
        assertEquals("1m 45s", formatDurationShort(105_000))
        assertEquals("2m", formatDurationShort(120_000))
    }
}
