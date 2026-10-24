package de.dimskiy.waypoints.platform.karooservices

import de.dimskiy.waypoints.platform.offline.*
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class PoiFieldStreamTest {
    private class Output {
        val states = mutableListOf<StreamState>()
        val cancel = slot<() -> Unit>()
        val emitter = mockk<Emitter<StreamState>>(relaxed = true)
        init {
            every { emitter.onNext(capture(states)) } just Runs
            every { emitter.setCancellable(capture(cancel)) } just Runs
        }
    }
    private val point = PoiPosition(55.6, 13.0)
    private val region = InstalledRegion("test", "Test", "1", 1)
    private val poi = OfflinePoi("node/1", "Water", point, setOf("water.drinking_water"), "{}", 1234.5)

    @Test fun nativeStreamHasImmediateStateAndDoesNotSearchWithoutGps() = runTest {
        val store = mockk<RegionPackStore>()
        every { store.installed() } returns listOf(region)
        val output = Output()
        val field = PoiFieldType(Provider { store }, StandardTestDispatcher(testScheduler), { flowOf(null) }, elapsedTime = { 1000 })
        field.startStream(output.emitter)
        assertEquals(listOf(StreamState.NotAvailable), output.states)
        runCurrent()
        assertEquals(StreamState.NotAvailable, output.states.last())
        verify(exactly = 0) { store.search(any(), any()) }
        output.cancel.captured()
        advanceUntilIdle()
    }

    @Test fun nearestStreamsRawDistanceAndCancelsLocationCollector() = runTest {
        val store = mockk<RegionPackStore>()
        every { store.installed() } returns listOf(region)
        every { store.search(any(), any()) } returns PoiPage(listOf(poi), 1, false)
        var closed = false
        val source = flow {
            try { emit(PoiFieldFix(point, 1000)); awaitCancellation() }
            finally { closed = true }
        }
        val output = Output()
        PoiFieldType(Provider { store }, StandardTestDispatcher(testScheduler), { source }, elapsedTime = { 1000 })
            .startStream(output.emitter)
        runCurrent()
        val data = (output.states.last() as StreamState.Streaming).dataPoint
        assertEquals(1234.5, data.values[DataType.Field.SINGLE]!!, 0.0)
        assertTrue(data.dataTypeId.endsWith("nearest-poi"))
        output.cancel.captured()
        advanceUntilIdle()
        assertTrue(closed)
        verify(exactly = 1) { store.search(any(), any()) }
    }

    @Test fun selectedRequiresTwoDistinctFreshFixesAndNeverOpensPacks() = runTest {
        val repo = mockk<SelectedPoiRepository>()
        val target = MutableStateFlow<SelectedPoi?>(SelectedPoi("one", "Water", point, emptySet()))
        every { repo.selected } returns target
        coEvery { repo.clearIfSelected("one") } coAnswers { target.value = null }
        val fixes = MutableStateFlow<PoiFieldFix?>(PoiFieldFix(point, 1000))
        val output = Output()
        PoiFieldType(Provider { error("Selected field must not open packs") }, StandardTestDispatcher(testScheduler),
            { fixes }, repo, elapsedTime = { 7000 }).startStream(output.emitter)
        runCurrent()
        assertTrue(output.states.last() is StreamState.Streaming)
        advanceTimeBy(5000); runCurrent()
        coVerify(exactly = 0) { repo.clearIfSelected(any()) }
        fixes.value = PoiFieldFix(point, 6000)
        runCurrent(); advanceTimeBy(5000); runCurrent()
        coVerify(exactly = 1) { repo.clearIfSelected("one") }
        assertEquals(StreamState.NotAvailable, output.states.last())
        output.cancel.captured(); advanceUntilIdle()
    }

    @Test fun staleGpsCannotClearTarget() = runTest {
        val repo = mockk<SelectedPoiRepository>()
        every { repo.selected } returns flowOf(SelectedPoi("one", "Water", point, emptySet()))
        val output = Output()
        PoiFieldType(Provider { error("No pack read") }, StandardTestDispatcher(testScheduler),
            { flowOf(PoiFieldFix(point, 1000)) }, repo, elapsedTime = { 31_000 }).startStream(output.emitter)
        runCurrent(); advanceTimeBy(5000); runCurrent()
        assertTrue(output.states.all { it == StreamState.NotAvailable })
        coVerify(exactly = 0) { repo.clearIfSelected(any()) }
        output.cancel.captured(); advanceUntilIdle()
    }

    @Test fun failedSearchCanRecoverOnNextRefresh() = runTest {
        val store = mockk<RegionPackStore>()
        every { store.installed() } returns listOf(region)
        every { store.search(any(), any()) } throws IllegalStateException("Snapshot unavailable") andThen PoiPage(listOf(poi), 1, false)
        val output = Output()
        PoiFieldType(Provider { store }, StandardTestDispatcher(testScheduler),
            { flowOf(PoiFieldFix(point, 1000)) }, elapsedTime = { 1000 }).startStream(output.emitter)
        runCurrent()
        assertEquals(StreamState.NotAvailable, output.states.last())
        advanceTimeBy(5000); runCurrent()
        assertTrue(output.states.last() is StreamState.Streaming)
        output.cancel.captured(); advanceUntilIdle()
    }
}
