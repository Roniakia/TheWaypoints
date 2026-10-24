package de.dimskiy.waypoints.platform.karooservices

import io.hammerhead.karooext.models.*
import org.junit.Assert.*
import org.junit.Test

class PoiFieldDistanceTest {
    @Test fun distancesRemainMetresForKarooUnitFormatting() {
        for (metres in listOf(0.0, 999.9, 1000.0, 12345.0)) {
            val state = PoiFieldReading(metres, null).streamState("selected") as StreamState.Streaming
            assertEquals("selected", state.dataPoint.dataTypeId)
            assertEquals(metres, state.dataPoint.values[DataType.Field.SINGLE]!!, 0.0)
        }
    }
    @Test fun unavailableDistancesNeverBecomeZeroDistance() {
        for (metres in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertEquals(StreamState.NotAvailable, PoiFieldReading(metres).streamState("nearest"))
        }
    }
}
