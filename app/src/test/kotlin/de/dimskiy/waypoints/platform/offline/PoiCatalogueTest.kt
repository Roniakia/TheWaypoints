package de.dimskiy.waypoints.platform.offline

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

internal fun catalogueSeed(): Pair<ByteArray, ByteArray> = listOf("catalog.json", "catalog.json.sig").map {
    PoiCatalogueTest::class.java.classLoader!!.getResourceAsStream("poi-catalogue/$it")!!.use { input -> input.readBytes() }
}.let { it[0] to it[1] }

class PoiCatalogueTest {
    @Test fun verifiesPublishedCatalogueAgainstPinnedKey() {
        val (bytes, signature) = catalogueSeed()
        val catalogue = PoiCatalogueVerifier.verify(bytes, signature)
        assertEquals("Skåne", catalogue.regions.single().name)
        assertEquals(19159, catalogue.regions.single().descriptor.poiCount)
    }

    @Test fun rejectsTamperingAndWrongSignatures() {
        val (bytes, signature) = catalogueSeed()
        val changed = bytes.copyOf().apply { this[10] = (this[10].toInt() xor 1).toByte() }
        for ((data, sig) in listOf(changed to signature, bytes to "AAAA".toByteArray())) {
            try { PoiCatalogueVerifier.verify(data, sig); fail("Tampering accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun restrictsAssetUrlsToOurDataReleases() {
        PoiCatalogueVerifier.requireReleaseAsset("https://github.com/Roniakia/WaypointsKaroo/releases/download/poi-data-test/catalog.json")
        for (url in listOf("http://github.com/Roniakia/WaypointsKaroo/releases/download/poi-data-test/catalog.json",
            "https://evil.test/Roniakia/WaypointsKaroo/releases/download/poi-data-test/catalog.json",
            "https://github.com/Roniakia/WaypointsKaroo/releases/download/v0.94/app.apk",
            "https://github.com/Roniakia/WaypointsKaroo/releases/download/poi-data-test/../secret")) {
            try { PoiCatalogueVerifier.requireReleaseAsset(url); fail(url) } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun mixedSelectionDownloadsMissingAndSkipsUnchanged() {
        val (bytes, signature) = catalogueSeed()
        val original = PoiCatalogueVerifier.verify(bytes, signature).regions.single()
        val other = original.copy(descriptor = original.descriptor.copy(id = "se-other"))
        val catalogue = VerifiedCatalogue(listOf(original, other), "")
        val installed = listOf(InstalledRegion(original.descriptor.id, original.name, original.descriptor.revision,
            original.descriptor.poiCount, original.descriptor.sha256))
        assertTrue(regionsToUpdate(catalogue, installed, emptySet()).isEmpty())
        assertEquals(listOf(other), regionsToUpdate(catalogue, installed, setOf(original.descriptor.id, other.descriptor.id)))
        assertEquals(listOf(original), regionsToUpdate(catalogue, installed.map { it.copy(sha256 = null) }, emptySet()))
        assertTrue(regionsToUpdate(catalogue, installed.map { it.copy(sha256 = null, dataDate = "2099-01-01") }, emptySet()).isEmpty())
    }

    @Test fun regionSearchAndPositionRecommendationPreserveOriginalNames() {
        val (bytes, signature) = catalogueSeed()
        val region = PoiCatalogueVerifier.verify(bytes, signature).regions.single()
        assertTrue(region.matches("skane")); assertTrue(region.matches("Europe Sweden")); assertFalse(region.matches("Finland"))
        assertEquals(listOf("se-skane"), recommendedRegionIds(listOf(region), PoiPosition(55.605, 13.004), "US"))
        assertEquals(listOf("se-skane"), recommendedRegionIds(listOf(region), null, "SE"))
        assertTrue(recommendedRegionIds(listOf(region), null, "FI").isEmpty())
    }

    @Test fun containingRegionRanksAheadOfNearestNeighborAndRespectsHoles() {
        val (bytes, signature) = catalogueSeed()
        val base = PoiCatalogueVerifier.verify(bytes, signature).regions.single()
        fun region(id: String, geometry: String) = base.copy(descriptor = base.descriptor.copy(id = id), geometry = JsonParser.parseString(geometry).asJsonObject)
        val hole = region("hole", """{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]]}""")
        val containing = region("inside", """{"type":"Polygon","coordinates":[[[4.5,4.5],[5.5,4.5],[5.5,5.5],[4.5,5.5],[4.5,4.5]]]}""")
        assertEquals("inside", recommendedRegionIds(listOf(hole, containing), PoiPosition(5.0, 5.0), "").first())
    }
}
