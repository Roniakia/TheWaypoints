package de.dimskiy.waypoints.platform.offline

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PoiCatalogueRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "catalogue-${UUID.randomUUID()}").apply { mkdirs() }
    @After fun clean() { root.deleteRecursively() }

    @Test fun verifiedCacheSurvivesInvalidRefreshAndRestart() {
        val bytes = context.assets.open("poi-catalogue/catalog.json").use { it.readBytes() }
        val signature = context.assets.open("poi-catalogue/catalog.json.sig").use { it.readBytes() }
        val transport = mockk<PackTransport>()
        val base = "https://github.com/Roniakia/TheWaypoints/releases/download/poi-data-2026-10-prototype.2"
        every { transport.read(PoiCatalogueRepository.RELEASES_URL, any(), any()) } returns """[{"tag_name":"poi-data-2026-10-prototype.2","draft":false,"published_at":"2026-10-21T00:00:00Z","assets":[{"name":"catalog.json","browser_download_url":"$base/catalog.json"},{"name":"catalog.json.sig","browser_download_url":"$base/catalog.json.sig"}]}]""".toByteArray()
        every { transport.read("$base/catalog.json", any(), any()) } returns bytes
        every { transport.read("$base/catalog.json.sig", any(), any()) } returns signature
        val file = File(root, "cache.json")
        val repository = PoiCatalogueRepository(file, transport) { bytes to signature }
        assertEquals("Skåne", repository.refresh().regions.single().name)
        every { transport.read("$base/catalog.json", any(), any()) } returns "unsigned".toByteArray()
        try { repository.refresh(); fail("Unsigned catalogue accepted") } catch (_: IllegalArgumentException) { }
        assertEquals("Skåne", PoiCatalogueRepository(file, transport) { error("Cache should be used") }.load().regions.single().name)
        file.writeText("corrupt cache")
        assertEquals("Skåne", repository.load().regions.single().name)
    }
}
