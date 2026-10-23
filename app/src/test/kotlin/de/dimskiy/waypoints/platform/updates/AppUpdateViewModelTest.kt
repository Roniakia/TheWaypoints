package de.dimskiy.waypoints.platform.updates

import androidx.lifecycle.ViewModelStore
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    @Test fun combinedDownloadRevalidatesAndOpensInstaller() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val repository = mockk<AppUpdates>()
        val release = AppRelease("0.97", 9, "v0.97", "Notes", "https://example.invalid/a.apk", 3, "ab".repeat(32))
        val file = File("/unused/update.apk")
        coEvery { repository.channel() } returns UpdateChannel.STABLE
        coEvery { repository.lastChecked() } returns 100L
        coEvery { repository.check(any()) } returns (true to release)
        coEvery { repository.download(release, any()) } coAnswers { arg<(Int) -> Unit>(1)(75); file }
        coEvery { repository.verify(file, release) } just Runs
        try {
            val vm = AppUpdateViewModel(repository); owner.put("updater", vm)
            advanceUntilIdle()
            assertEquals(release, vm.state.value.release)
            vm.downloadAndInstall(); advanceUntilIdle()
            assertEquals(file, vm.state.value.apk)
            val event = async { vm.installs.first() }
            advanceUntilIdle()
            assertEquals(file, event.await())
            coVerify(exactly = 1) { repository.verify(file, release) }
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun channelSwitchChecksNewChannelAndFailedDownloadCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val repository = mockk<AppUpdates>()
        val release = AppRelease("0.97", 9, "v0.97-beta.1", "Notes", "https://example.invalid/a.apk", 3, "ab".repeat(32))
        coEvery { repository.channel() } returnsMany listOf(UpdateChannel.STABLE, UpdateChannel.BETA)
        coEvery { repository.channel(UpdateChannel.BETA) } just Runs
        coEvery { repository.lastChecked() } returns null
        coEvery { repository.check(any()) } returns (true to release)
        coEvery { repository.download(any(), any()) } throws java.io.IOException("offline")
        try {
            val vm = AppUpdateViewModel(repository); owner.put("updater", vm)
            advanceUntilIdle(); vm.channel(UpdateChannel.BETA); advanceUntilIdle()
            assertEquals(UpdateChannel.BETA, vm.state.value.channel)
            vm.downloadAndInstall(); advanceUntilIdle()
            assertFalse(vm.state.value.downloading)
            assertNull(vm.state.value.apk)
            assertTrue(vm.state.value.message!!.contains("offline"))
            vm.downloadAndInstall(); advanceUntilIdle()
            coVerify(exactly = 2) { repository.download(any(), any()) }
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
    @Test fun cancellationDoesNotExposeAnInstallerEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val repository = mockk<AppUpdates>()
        val release = AppRelease("0.97", 9, "v0.97", "", "https://example.invalid/a.apk", 3, "ab".repeat(32))
        coEvery { repository.channel() } returns UpdateChannel.STABLE
        coEvery { repository.lastChecked() } returns null
        coEvery { repository.check(any()) } returns (true to release)
        coEvery { repository.download(any(), any()) } coAnswers { awaitCancellation() }
        try {
            val vm = AppUpdateViewModel(repository); owner.put("updater", vm)
            advanceUntilIdle(); vm.downloadAndInstall(); runCurrent()
            assertTrue(vm.state.value.downloading)
            vm.cancel(); advanceUntilIdle()
            assertFalse(vm.state.value.downloading)
            assertNull(vm.state.value.apk)
            coVerify(exactly = 0) { repository.verify(any(), any()) }
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
