package de.dimskiy.waypoints.platform.offline

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SelectedPoiRepositoryTest {
    @Test fun selectionSurvivesReopeningAndArrivalClearsIt() = runBlocking {
        val directory = Files.createTempDirectory("selected-poi").toFile()
        val file = directory.resolve("target.preferences_pb")
        var job = SupervisorJob()
        fun repository() = SelectedPoiRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file }, Dispatchers.IO)
        try {
            val initial = repository()
            assertNull(initial.selected.first())
            initial.select("Water", PoiPosition(55.6, 13.0), setOf("water.drinking_water"))
            val target = initial.selected.first()!!
            job.cancelAndJoin()
            job = SupervisorJob()
            val reopened = repository()
            assertEquals(target, reopened.selected.first())
            reopened.clearIfSelected(target.token)
            assertNull(reopened.selected.first())
        } finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }

    @Test fun oldArrivalCannotClearReplacementOrReselection() = runBlocking {
        val directory = Files.createTempDirectory("selected-poi-race").toFile()
        val job = SupervisorJob()
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                directory.resolve("target.preferences_pb")
            }
            val repo = SelectedPoiRepository(preferences, Dispatchers.IO)
            repo.select("Water", PoiPosition(55.6, 13.0), setOf("water.drinking_water"))
            val first = repo.selected.first()!!
            repo.select("Cafe", PoiPosition(55.7, 13.1), setOf("food.cafe"))
            val cafe = repo.selected.first()!!
            repo.clearIfSelected(first.token)
            assertEquals(cafe, repo.selected.first())
            repo.select("Cafe", cafe.position, cafe.categories)
            val reselected = repo.selected.first()!!
            assertNotEquals(cafe.token, reselected.token)
            repo.clearIfSelected(cafe.token)
            assertEquals(reselected, repo.selected.first())
        } finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }

    @Test fun malformedSelectionIsTreatedAsEmpty() = runBlocking {
        val directory = Files.createTempDirectory("selected-poi-invalid").toFile()
        val job = SupervisorJob()
        try {
            val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) {
                directory.resolve("target.preferences_pb")
            }
            val repo = SelectedPoiRepository(preferences, Dispatchers.IO)
            preferences.edit { it[stringPreferencesKey("selectedRidePoi")] = "broken" }
            assertNull(repo.selected.first())
            preferences.edit { it[stringPreferencesKey("selectedRidePoi")] =
                """{"token":"x","name":"bad","latitude":91,"longitude":0,"categories":[]}""" }
            assertNull(repo.selected.first())
        } finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
}

class PoiArrivalTrackerTest {
    @Test fun requiresTwoDifferentFixesWithinFiftyMetres() {
        val arrival = PoiArrivalTracker()
        assertFalse(arrival.arrived("one", 50.0, 1000))
        assertFalse(arrival.arrived("one", 50.0, 1000))
        assertTrue(arrival.arrived("one", 40.0, 6000))
    }
    @Test fun outsideRadiusResetsConfirmation() {
        val arrival = PoiArrivalTracker()
        assertFalse(arrival.arrived("one", 40.0, 1000))
        assertFalse(arrival.arrived("one", 50.1, 6000))
        assertFalse(arrival.arrived("one", 30.0, 11000))
        assertTrue(arrival.arrived("one", 20.0, 16000))
    }
    @Test fun newSelectionRequiresNewConfirmation() {
        val arrival = PoiArrivalTracker()
        assertFalse(arrival.arrived("one", 20.0, 1000))
        assertFalse(arrival.arrived("two", 20.0, 6000))
        assertTrue(arrival.arrived("two", 20.0, 11000))
    }
    @Test fun missingOrInvalidFixResetsConfirmation() {
        listOf(null, Double.NaN, -1.0, Double.POSITIVE_INFINITY).forEach { invalid ->
            val arrival = PoiArrivalTracker()
            assertFalse(arrival.arrived("one", 20.0, 1000))
            assertFalse(arrival.arrived("one", invalid, 6000))
            assertFalse(arrival.arrived("one", 20.0, 11000))
        }
        val arrival = PoiArrivalTracker()
        assertFalse(arrival.arrived("one", 20.0, 1000))
        assertFalse(arrival.arrived("one", 20.0, null))
        assertFalse(arrival.arrived("one", 20.0, 11000))
    }
    @Test fun distanceIsStableAcrossDateLineAndAtSamePoint() {
        assertEquals(0.0, poiDistanceMetres(PoiPosition(55.0, 13.0), PoiPosition(55.0, 13.0)), 0.001)
        assertEquals(222.39, poiDistanceMetres(PoiPosition(0.0, 179.999), PoiPosition(0.0, -179.999)), 0.1)
    }
}
