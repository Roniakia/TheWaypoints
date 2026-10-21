package de.dimskiy.waypoints.platform.karooservices

import io.hammerhead.karooext.models.LaunchPinDrop
import io.hammerhead.karooext.models.Symbol
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PinDropSerializationTest {
    @Test
    fun sdkPinDropSurvivesSerializationWithSharedRuntime() {
        val request = LaunchPinDrop(Symbol.POI(id = "test-poi", lat = 59.3293, lng = 18.0686))
        val encoded = Json.encodeToString(LaunchPinDrop.serializer(), request)
        assertEquals(request, Json.decodeFromString(LaunchPinDrop.serializer(), encoded))
    }
}
