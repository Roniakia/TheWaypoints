package de.dimskiy.waypoints.platform.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import de.dimskiy.waypoints.domain.model.Waypoint
import de.dimskiy.waypoints.domain.waypointsrepository.WaypointsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OfflineFavoritesTest {
    @Test fun favoritesFilterPageAndReactToRemovalWithoutInstalledRegions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val saved = MutableStateFlow((1..55).map { i -> Waypoint.Stored(i, "osm:node/$i", "Water $i",
                Waypoint.Address(null, null, null, null, null, "water.drinking_water", null), 55.6, 13.0) })
            val bookmarks = mockk<WaypointsRepository>()
            every { bookmarks.observeWaypointsStored() } returns saved
            val catalogue = mockk<PoiCatalogueRepository>()
            every { catalogue.load() } returns VerifiedCatalogue(emptyList(), "")
            val store = mockk<RegionPackStore>()
            every { store.installed() } returns emptyList()
            val downloads = mockk<RegionDownloads>()
            every { downloads.observe() } returns emptyFlow()
            val preferences = mockk<DataStore<Preferences>>()
            every { preferences.data } returns emptyFlow()
            val target = mockk<SelectedPoiRepository>(relaxed = true)
            val map = mockk<de.dimskiy.waypoints.domain.cases.OpenExternalMap>(relaxed = true)
            val vm = OfflineViewModel(catalogue, Provider { store }, downloads, bookmarks,
                mockk(), map, preferences, StandardTestDispatcher(testScheduler), target)
            owner.put("favorites", vm)
            runCurrent()
            vm.page(OfflinePage.FAVORITES)
            advanceUntilIdle()
            assertEquals(50, vm.state.value.rows.size)
            assertEquals(55, vm.state.value.total)
            assertTrue(vm.state.value.hasMore)
            vm.search(more = true)
            advanceUntilIdle()
            assertEquals(55, vm.state.value.rows.size)
            vm.poiQuery("Water 55")
            advanceUntilIdle()
            assertEquals(listOf("Water 55"), vm.state.value.rows.map { it.waypoint.name })
            vm.applyFilters(setOf("food.cafe"), PoiSort.NAME)
            advanceUntilIdle()
            assertTrue(vm.state.value.rows.isEmpty())
            vm.applyFilters(setOf("water.drinking_water"), PoiSort.NAME)
            advanceUntilIdle()
            saved.value = saved.value.filter { it.id != 55 }
            advanceUntilIdle()
            assertTrue(vm.state.value.rows.isEmpty())
            vm.page(OfflinePage.POIS)
            assertEquals("", vm.state.value.poiQuery)
            vm.page(OfflinePage.FAVORITES)
            advanceUntilIdle()
            assertEquals("Water 55", vm.state.value.poiQuery)
            assertEquals(setOf("water.drinking_water"), vm.state.value.types)
            assertEquals(PoiSort.NAME, vm.state.value.sort)
            val row = OfflineRow(saved.value.first(), setOf("water.drinking_water"), 100.0)
            vm.open(row)
            advanceUntilIdle()
            coVerifyOrder {
                target.select(row.waypoint.name, PoiPosition(row.waypoint.latitude, row.waypoint.longitude), row.categories)
                map(row.waypoint)
            }
            verify(exactly = 0) { store.search(any(), any()) }
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }
    @Test fun swipeHintWaitsForFavoritesAndPersistsAcrossViewModelAndStoreReopen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val directory = java.nio.file.Files.createTempDirectory("poi-hint").toFile()
        val owner = ViewModelStore()
        var storeJob = kotlinx.coroutines.SupervisorJob()
        val saved = MutableStateFlow<List<Waypoint.Stored>>(emptyList())
        val bookmarks = mockk<WaypointsRepository>()
        every { bookmarks.observeWaypointsStored() } returns saved
        val catalogue = mockk<PoiCatalogueRepository>()
        every { catalogue.load() } returns VerifiedCatalogue(emptyList(), "")
        every { catalogue.refresh(any()) } returns VerifiedCatalogue(emptyList(), "")
        val store = mockk<RegionPackStore>()
        every { store.installed() } returns emptyList()
        every { store.search(any(), any()) } returns PoiPage(emptyList(), 0, false)
        val downloads = mockk<RegionDownloads>()
        every { downloads.observe() } returns emptyFlow()
        fun preferences() = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(storeJob + Dispatchers.IO)) { directory.resolve("hint.preferences_pb") }
        fun viewModel(preferences: DataStore<Preferences>) = OfflineViewModel(catalogue, Provider { store }, downloads,
            bookmarks, mockk(), mockk(), preferences, StandardTestDispatcher(testScheduler), mockk())
        try {
            val prefs = preferences()
            val initial = viewModel(prefs)
            owner.put("initial", initial)
            val first = initial.state.first { it.ready && it.favoritesLoaded }
            assertFalse(first.hasFavorites)
            assertFalse(first.swipeHintSeen)
            initial.swipeHintShown()
            val key = androidx.datastore.preferences.core.booleanPreferencesKey("poiSwipeHintSeen")
            prefs.data.first { it[key] == true }
            owner.clear()
            storeJob.cancel()
            storeJob.join()
            storeJob = kotlinx.coroutines.SupervisorJob()
            val reopened = viewModel(preferences())
            owner.put("reopened", reopened)
            val state = reopened.state.first { it.ready && it.favoritesLoaded }
            assertFalse(state.hasFavorites)
            assertTrue(state.swipeHintSeen)
            saved.value = listOf(Waypoint.Stored(1, "water", "Water",
                Waypoint.Address(null, null, null, null, null, null, null), 55.6, 13.0))
            runCurrent()
            assertTrue(reopened.state.value.hasFavorites)
            saved.value = emptyList()
            runCurrent()
            assertFalse(reopened.state.value.hasFavorites)
            assertTrue(reopened.state.value.swipeHintSeen)
        } finally {
            owner.clear()
            storeJob.cancel()
            storeJob.join()
            directory.deleteRecursively()
            Dispatchers.resetMain()
        }
    }

}
