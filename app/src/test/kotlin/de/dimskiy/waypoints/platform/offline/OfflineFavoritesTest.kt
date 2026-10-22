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
            val vm = OfflineViewModel(catalogue, Provider { store }, downloads, bookmarks,
                mockk(), mockk(), preferences, StandardTestDispatcher(testScheduler))
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
            verify(exactly = 0) { store.search(any(), any()) }
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }
}
