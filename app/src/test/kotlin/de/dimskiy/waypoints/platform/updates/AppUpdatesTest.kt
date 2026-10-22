package de.dimskiy.waypoints.platform.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import de.dimskiy.waypoints.BuildConfig
import de.dimskiy.waypoints.platform.offline.PackTransport
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import javax.inject.Provider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppUpdatesTest {
    @get:Rule val temporary = TemporaryFolder()
    private class PreferencesStore : DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data = state.asStateFlow()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value); state.value = updated; return updated
        }
    }
    private val source = mockk<UpdateSource>()
    private val transport = mockk<PackTransport>()
    private val context = mockk<Context>()
    private val pm = mockk<PackageManager>()
    private val prefs = PreferencesStore()
    private var now = 1_000_000L
    private fun release(code: Long = BuildConfig.VERSION_CODE + 1L, bytes: ByteArray = "APK".toByteArray()) =
        AppRelease("0.97", code, "v0.97", "Notes", "https://github.com/Roniakia/TheWaypoints/releases/download/v0.97/app.apk",
            bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private fun repository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): AppUpdates {
        every { context.cacheDir } returns temporary.root
        every { context.packageName } returns BuildConfig.APPLICATION_ID
        every { context.packageManager } returns pm
        return AppUpdates(context, prefs, dispatcher, Provider { now }, source, transport)
    }
    @Test fun dailyThrottleManualBypassChannelPersistenceAndNoDowngrade() = runTest {
        every { source.latest(any(), any(), any()) } returns release(BuildConfig.VERSION_CODE.toLong())
        val repository = repository(StandardTestDispatcher(testScheduler))
        assertNull(repository.check(false).second)
        assertFalse(repository.check(false).first)
        assertTrue(repository.check(true).first)
        now += 86_400_000
        assertTrue(repository.check(false).first)
        repository.channel(UpdateChannel.BETA)
        assertEquals(UpdateChannel.BETA, repository.channel())
        assertTrue(repository.check(false).first)
        verify(exactly = 4) { source.latest(any(), any(), any()) }
        assertEquals(now, repository.lastChecked())
        assertEquals(UpdateChannel.BETA, repository(StandardTestDispatcher(testScheduler)).channel())
    }
    @Test fun failedNetworkCheckIsThrottledButCanBeRetriedManually() = runTest {
        every { source.latest(any(), any(), any()) } throws java.io.IOException("offline")
        val repository = repository(StandardTestDispatcher(testScheduler))
        try { repository.check(false); fail() } catch (_: java.io.IOException) { }
        assertNull(repository.lastChecked())
        assertFalse(repository.check(false).first)
        try { repository.check(true); fail() } catch (_: java.io.IOException) { }
        verify(exactly = 2) { source.latest(any(), any(), any()) }
    }
    @Suppress("DEPRECATION")
    private fun packageInfo(code: Int, certificate: String, pkg: String = BuildConfig.APPLICATION_ID): PackageInfo {
        val signature = mockk<Signature>()
        every { signature.toByteArray() } returns certificate.toByteArray()
        return PackageInfo().apply { packageName = pkg; versionCode = code; versionName = "0.97"; signatures = arrayOf(signature) }
    }
    @Test fun downloadRejectsChecksumMismatchAndRemovesFile() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        every { transport.download(any(), any(), any(), any(), any()) } answers { arg<File>(1).writeText("BAD") }
        try { repository.download(release()) {}; fail() } catch (_: IllegalArgumentException) { }
        assertTrue(File(temporary.root, "app-updates").listFiles().orEmpty().isEmpty())
        verify(exactly = 0) { pm.getPackageArchiveInfo(any(), any<Int>()) }
    }
    @Test fun validApkRechecksBeforeInstallAndRejectsTampering() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        every { transport.download(any(), any(), any(), any(), any()) } answers { arg<File>(1).writeText("APK") }
        every { pm.getPackageArchiveInfo(any(), any<Int>()) } returns packageInfo(BuildConfig.VERSION_CODE + 1, "our key")
        every { pm.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo(BuildConfig.VERSION_CODE, "our key")
        val file = repository.download(release()) {}
        repository.verify(file, release())
        file.writeText("BAD")
        try { repository.verify(file, release()); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsWrongSigningKeyPackageAndVersion() = runTest {
        val repository = repository(StandardTestDispatcher(testScheduler))
        every { transport.download(any(), any(), any(), any(), any()) } answers { arg<File>(1).writeText("APK") }
        every { pm.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo(BuildConfig.VERSION_CODE, "our key")
        for (apk in listOf(packageInfo(BuildConfig.VERSION_CODE + 1, "other key"),
            packageInfo(BuildConfig.VERSION_CODE + 1, "our key", "other.app"), packageInfo(BuildConfig.VERSION_CODE, "our key"))) {
            every { pm.getPackageArchiveInfo(any(), any<Int>()) } returns apk
            try { repository.download(release()) {}; fail() } catch (_: IllegalArgumentException) { }
        }
        assertTrue(File(temporary.root, "app-updates").listFiles().orEmpty().isEmpty())
    }
}
