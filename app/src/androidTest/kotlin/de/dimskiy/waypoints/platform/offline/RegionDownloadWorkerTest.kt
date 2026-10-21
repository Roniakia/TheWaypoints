package de.dimskiy.waypoints.platform.offline

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.gson.JsonParser
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class RegionDownloadWorkerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "worker-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = RegionPackStore(File(root, "regions"))
    private val transport = mockk<PackTransport>()
    private val catalogue = mockk<PoiCatalogueRepository>()
    private val downloads = RegionDownloads(mockk(relaxed = true), File(root, "downloads")) { 1L }
    private val services = mockk<OfflineWorkerServices>().apply {
        every { store() } returns this@RegionDownloadWorkerTest.store
        every { transport() } returns this@RegionDownloadWorkerTest.transport
        every { catalogue() } returns this@RegionDownloadWorkerTest.catalogue
        every { downloads() } returns this@RegionDownloadWorkerTest.downloads
        every { ioDispatcher() } returns Dispatchers.IO
    }
    @After fun clean() { root.deleteRecursively() }

    private fun fixture(): Pair<File, PackDescriptor> {
        val zip = File(root, "fixture.zip")
        instrumentation.context.assets.open("poi-packs/fixture.zip").use { input -> zip.outputStream().use(input::copyTo) }
        val json = instrumentation.context.assets.open("poi-packs/descriptor.json").bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val descriptor = PackDescriptor("se-test", "fixture-1", json.get("sha256").asString,
            json.get("download_bytes").asLong, json.get("database_bytes").asLong, json.get("poi_count").asLong)
        val seed = PoiCatalogueVerifier.verify(context.assets.open("poi-catalogue/catalog.json").use { it.readBytes() },
            context.assets.open("poi-catalogue/catalog.json.sig").use { it.readBytes() })
        every { catalogue.load() } returns VerifiedCatalogue(listOf(seed.regions.single().copy(descriptor = descriptor, dataDate = "2026-10-21")), "")
        return zip to descriptor
    }

    private fun worker(id: String = "se-test", servicesForTest: OfflineWorkerServices = services) = TestListenableWorkerBuilder<RegionDownloadWorker>(context)
        .setInputData(workDataOf(RegionDownloadWorker.REGION_ID to id))
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                RegionDownloadWorker(appContext, workerParameters, servicesForTest)
        }).build()

    @Test fun downloadsInstallsAndSkipsUnchanged() = runBlocking {
        val (zip, _) = fixture()
        every { transport.download(any(), any(), any(), any(), any()) } answers {
            zip.copyTo(arg(1), overwrite = true); arg<(Long) -> Unit>(3)(zip.length())
        }
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(66, store.search(PoiSearch()).total)
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        verify(exactly = 1) { transport.download(any(), any(), any(), any(), any()) }
    }

    @Test fun checksumFailurePreservesOldSnapshot() = runBlocking {
        val (_, descriptor) = fixture()
        val zip = File(root, "fixture.zip")
        store.install(zip, descriptor.copy(revision = "fixture-1"))
        val current = catalogue.load()
        every { catalogue.load() } returns VerifiedCatalogue(current.regions.map { it.copy(descriptor = it.descriptor.copy(revision = "fixture-2")) }, "")
        every { transport.download(any(), any(), any(), any(), any()) } answers { arg<File>(1).writeBytes(ByteArray(descriptor.downloadBytes.toInt())) }
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("fixture-1", store.installed().single().revision)
        assertEquals(66, store.search(PoiSearch()).total)
    }

    @Test fun retriesNetworkFailureAndHonorsCancellationBeforeInstall() = runBlocking {
        val (zip, _) = fixture()
        every { transport.download(any(), any(), any(), any(), any()) } throws IOException("offline")
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        every { transport.download(any(), any(), any(), any(), any()) } answers {
            zip.copyTo(arg(1), overwrite = true)
            downloads.cancel("se-test")
            arg<() -> Unit>(4)()
        }
        try { worker().doWork(); fail("Cancelled install ran") } catch (_: CancellationException) { }
        assertTrue(store.installed().isEmpty())
    }

    @Test fun optionalPublicReleaseDownloadAndInstall() = runBlocking {
        assumeTrue("Opt in to public GitHub networking with offlineLive=true", InstrumentationRegistry.getArguments().getString("offlineLive") == "true")
        val liveCatalogue = PoiCatalogueRepository(File(root, "catalogue.json"), HttpsPackTransport()) {
            context.assets.open("poi-catalogue/catalog.json").use { it.readBytes() } to context.assets.open("poi-catalogue/catalog.json.sig").use { it.readBytes() }
        }
        val refreshed = liveCatalogue.refresh()
        assertTrue(refreshed.regions.any { it.descriptor.id == "se-skane" })
        every { services.catalogue() } returns liveCatalogue
        every { services.transport() } returns HttpsPackTransport()
        assertEquals(ListenableWorker.Result.success(), worker("se-skane").doWork())
        assertEquals(19159, store.search(PoiSearch()).total)
    }
}
