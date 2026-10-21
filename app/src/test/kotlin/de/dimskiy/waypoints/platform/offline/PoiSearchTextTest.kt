package de.dimskiy.waypoints.platform.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class PoiSearchTextTest {
    @Test fun foldsLatinNamesAndAccents() {
        assertEquals("skane munchen lodz strasse", PoiSearchText.normalize("Skåne München Łódź Straße"))
        assertEquals("cafe", PoiSearchText.normalize("CAFE\u0301"))
    }

    @Test fun treatsOperatorsAsWordsAndRemovesSyntax() {
        assertEquals("bike* AND repair*", PoiSearchText.ftsQuery("bike repair"))
        assertEquals("or*", PoiSearchText.ftsQuery("\" OR *"))
        assertEquals("", PoiSearchText.ftsQuery("***\"()"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCoordinates() { PoiPosition(Double.NaN, 13.0) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedPages() { PoiSearch(pageSize = Int.MAX_VALUE) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRegionPathTraversal() { PackDescriptor("../escape", "1", "a".repeat(64), 1, 1, 1) }
}
