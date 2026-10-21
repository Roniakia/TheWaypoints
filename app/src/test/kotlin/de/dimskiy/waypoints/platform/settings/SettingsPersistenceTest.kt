package de.dimskiy.waypoints.platform.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.dimskiy.waypoints.domain.model.DeviceLocation
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPersistenceTest {
    @Test
    fun preferencesAndLocationSurviveReopeningDataStore() = runBlocking {
        val directory = Files.createTempDirectory("waypoints-settings").toFile()
        val file = directory.resolve("settings.preferences_pb")
        val location = DeviceLocation(59.3293, 18.0686, 123456789L)
        var job = SupervisorJob()
        fun provider() = SettingsProviderImpl(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { file },
            Dispatchers.IO,
        )
        try {
            val initial = provider()
            assertFalse(initial.observeGeoSearchEnabled().first())
            assertNull(initial.observeLastLocation().first())
            initial.setGeoSearchEnabled(true)
            initial.saveLocation(location)
            job.cancelAndJoin()
            job = SupervisorJob()
            val reopened = provider()
            assertEquals(true, reopened.observeGeoSearchEnabled().first())
            assertEquals(location, reopened.observeLastLocation().first())
            reopened.setGeoSearchEnabled(false)
            assertFalse(reopened.observeGeoSearchEnabled().first())
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
