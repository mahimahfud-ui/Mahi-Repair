package com.example.device

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceIntelligenceTest {
    @Test
    fun unknownAndNegativeValuesAreNotPresentedAsRealMeasurements() {
        assertEquals("Unavailable", DeviceIntelligence.formatBytes(null))
        assertEquals("Unavailable", DeviceIntelligence.formatBytes(-1L))
    }

    @Test
    fun byteFormattingUsesBinaryUnits() {
        assertEquals("0 B", DeviceIntelligence.formatBytes(0L))
        assertEquals("1.0 KB", DeviceIntelligence.formatBytes(1024L))
        assertEquals("1.0 MB", DeviceIntelligence.formatBytes(1024L * 1024L))
    }
}