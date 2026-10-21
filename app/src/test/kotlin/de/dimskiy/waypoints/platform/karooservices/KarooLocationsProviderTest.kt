package de.dimskiy.waypoints.platform.karooservices

import android.content.Context
import de.dimskiy.waypoints.DataResult
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnLocationChanged
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class KarooLocationsProviderTest {
    private val context = mockk<Context>().apply { every { getString(any()) } returns "Location unavailable" }
    private val service = mockk<KarooSystemService>(relaxed = true)
    private val connection = mockk<KarooServiceProvider>()

    private fun provider(dispatcher: kotlinx.coroutines.CoroutineDispatcher): KarooLocationsProvider {
        coEvery { connection.ensureConnected(any()) } coAnswers { arg<suspend (KarooSystemService) -> Unit>(0)(service) }
        return KarooLocationsProvider(connection, context, dispatcher, Provider { 123L })
    }

    @Test fun timeoutIncludesConnectionAndReturnsFallback() = runTest {
        val provider = provider(StandardTestDispatcher(testScheduler))
        coEvery { connection.ensureConnected(any()) } coAnswers { awaitCancellation() }
        val result = async { provider.observeDeviceLocations().first { !it.isLoading() } }
        advanceTimeBy(10_001); runCurrent()
        assertTrue(result.await() is DataResult.Error)
    }

    @Test fun cancellationRemovesLocationConsumer() = runTest {
        val removed = mutableListOf<String>()
        val observer = launch { locationEvents({ "location-test" }, { removed += it }).collect() }
        runCurrent()
        observer.cancelAndJoin()
        assertEquals(listOf("location-test"), removed)
    }

    @Test fun duplicateCallbacksProduceOneResultAndRemoveConsumer() = runTest {
        val removed = mutableListOf<String>()
        val result = locationEvents({ emit ->
            emit(DataResult.ready(OnLocationChanged(55.6, 13.0, null)))
            emit(DataResult.ready(OnLocationChanged(55.7, 13.1, null)))
            "location-test"
        }, { removed += it }).first()
        assertEquals(55.6, result.data!!.lat, 0.0)
        assertEquals(listOf("location-test"), removed)
    }
}
