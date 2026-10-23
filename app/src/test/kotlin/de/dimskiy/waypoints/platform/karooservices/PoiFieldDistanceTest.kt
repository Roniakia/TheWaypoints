package de.dimskiy.waypoints.platform.karooservices

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PoiFieldDistanceTest {
    @Test fun missingOrInvalidDistance() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach {
            assertEquals("—", poiFieldDistance(it))
        }
    }
    @Test fun compactMetricDistance() {
        assertEquals("0m", poiFieldDistance(0.0))
        assertEquals("999m", poiFieldDistance(999.9))
        assertEquals("1.0km", poiFieldDistance(1000.0))
        assertEquals("12.3km", poiFieldDistance(12345.0))
    }
    @Test fun formattingDoesNotDependOnPhoneLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5km", poiFieldDistance(1500.0))
        } finally { Locale.setDefault(original) }
    }
}
