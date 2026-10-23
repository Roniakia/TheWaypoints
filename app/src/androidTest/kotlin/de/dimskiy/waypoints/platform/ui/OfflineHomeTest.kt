package de.dimskiy.waypoints.platform.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.dimskiy.waypoints.domain.model.Waypoint
import de.dimskiy.waypoints.platform.offline.*
import de.dimskiy.waypoints.platform.ui.screens.OfflineActions
import de.dimskiy.waypoints.platform.ui.screens.OfflineHomeContent
import de.dimskiy.waypoints.platform.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class OfflineHomeTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun catalogue(): VerifiedCatalogue = PoiCatalogueVerifier.verify(
        context.assets.open("poi-catalogue/catalog.json").use { it.readBytes() },
        context.assets.open("poi-catalogue/catalog.json.sig").use { it.readBytes() })

    @Test fun compactHeaderDownloadedOrderAndHiddenSelection() {
        val base = catalogue().regions.single()
        val halland = base.copy(name = "Halland", searchName = "halland", descriptor = base.descriptor.copy(id = "se-halland"))
        var state by mutableStateOf(OfflineUiState(ready = true, page = OfflinePage.REGIONS, catalogue = VerifiedCatalogue(listOf(base, halland), ""),
            installed = listOf(InstalledRegion("se-skane", "Skåne", base.descriptor.revision, 19159, base.descriptor.sha256)),
            recommended = listOf("se-halland"), selected = setOf("se-skane")))
        var updated = emptySet<String>()
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(regionQuery = { state = state.copy(regionQuery = it) }, update = { updated = state.selected })) } }
        val input = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("Choose page").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(input.center.y - menu.center.y) < 3)
        compose.onNodeWithText("Europe").performClick()
        compose.onNodeWithText("Sweden").performClick()
        assertTrue(compose.onNodeWithText("Skåne").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Halland").fetchSemanticsNode().boundsInRoot.top)
        capture("offline-regions.png")
        compose.onNode(hasSetTextAction()).performTextInput("halland")
        compose.onNodeWithText("Skåne").assertDoesNotExist()
        compose.onNodeWithText("Update selected · 1").assertExists()
        compose.onNodeWithText("Update selected · 1").performClick()
        compose.runOnIdle { assertEquals(setOf("se-skane"), updated) }
    }

    @Test fun nestedParentsHavePartialAndAllSelection() {
        val base = catalogue().regions.single()
        val other = base.copy(name = "Halland", searchName = "halland", descriptor = base.descriptor.copy(id = "se-halland"))
        var state by mutableStateOf(OfflineUiState(ready = true, page = OfflinePage.REGIONS, catalogue = VerifiedCatalogue(listOf(base, other), "")))
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(select = { ids, checked -> state = state.copy(selected = if (checked) state.selected + ids else state.selected - ids) })) } }
        compose.onNodeWithText("Europe").performClick()
        compose.onNodeWithText("Sweden").performClick()
        compose.onNodeWithContentDescription("Select Skåne").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Select Sweden").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
        compose.onNodeWithContentDescription("Select Sweden").performClick()
        compose.runOnIdle { assertEquals(setOf("se-skane", "se-halland"), state.selected) }
    }

    @Test fun filtersExpandTypesHaveNoNameInputAndResetDefaults() {
        var state by mutableStateOf(OfflineUiState(ready = true, page = OfflinePage.POIS, invitation = false, rows = (1..8).map { i ->
            OfflineRow(Waypoint.Discovered("osm:node/$i", if (i == 1) "Skåne café — a longer place name" else "Drinking water $i",
                Waypoint.Address(null, null, null, null, null, null, null), 55.6, 13.0), setOf(if (i == 1) "food.cafe" else "water.drinking_water"), i * 140.0)
        }))
        var applied = emptySet<String>()
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(preview = { _, _ -> state = state.copy(filterCount = 8) }, filters = { types, _ -> applied = types })) } }
        capture("offline-pois.png")
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("Bicycle services").performClick()
        compose.onNodeWithContentDescription("Select Bicycle services").performClick()
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size <= 1)
        capture("offline-filters.png")
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasContentDescription("Bicycle parking"))
        compose.onNodeWithContentDescription("Bicycle parking").assertIsOn()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Apply · 8 POIs").performClick()
        compose.runOnIdle { assertEquals(PoiTypes.defaults, applied); assertFalse("rest.bench" in applied); assertFalse("bicycle.parking" in applied) }
    }

    @Test fun clearSearchAndAutoloadOnlyOncePerPage() {
        var state by mutableStateOf(OfflineUiState(ready = true, page = OfflinePage.POIS,
            poiQuery = "water", hasMore = true, total = 100, rows = (1..50).map { i ->
                OfflineRow(Waypoint.Discovered("osm:node/$i", "Water $i",
                    Waypoint.Address(null, null, null, null, null, null, null), 55.6, 13.0), emptySet(), null)
            }))
        var requests = 0
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(
            poiQuery = { state = state.copy(poiQuery = it) }, more = { requests++ })) } }
        compose.onNodeWithContentDescription("Saved POIs").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.runOnIdle { assertEquals("", state.poiQuery); assertEquals(0, requests) }
        compose.onNode(hasSetTextAction()).performClick().assertIsFocused()
        compose.onNodeWithTag("poi-list").performTouchInput { swipeUp() }
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        compose.onNode(hasSetTextAction()).performClick().assertIsFocused()
        compose.onNodeWithTag("poi-list").performScrollToIndex(49)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, requests); state = state.copy(searching = true) }
        compose.runOnIdle { state = state.copy(searching = false) }
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun favoritesDestinationHasSearchAndFilters() {
        var state by mutableStateOf(OfflineUiState(ready = true, page = OfflinePage.POIS))
        var applied = emptySet<String>()
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(
            page = { state = state.copy(page = it) },
            poiQuery = { state = state.copy(poiQuery = it) },
            filters = { types, sort -> applied = types; state = state.copy(types = types, sort = sort) })) } }
        compose.onNodeWithContentDescription("Choose page").performClick()
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("Search favorites").assertExists()
        compose.onNodeWithText("No favorites added yet.").assertExists()
        compose.onNodeWithText("Browse POIs").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("water")
        compose.onNodeWithText("No matching favorite POIs.").assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("No favorites added yet.").assertExists()
        compose.runOnIdle { state = state.copy(types = PoiTypes.labels.keys) }
        compose.onNodeWithText("No favorites added yet.").assertExists()
        compose.runOnIdle { state = state.copy(types = setOf("food.cafe")) }
        compose.onNodeWithText("No matching favorite POIs.").assertExists()
        compose.runOnIdle { state = state.copy(types = emptySet()) }
        compose.onNodeWithText("No matching favorite POIs.").assertExists()
        compose.runOnIdle { state = state.copy(types = PoiTypes.defaults) }
        compose.onNodeWithContentDescription("Filters").performClick()
        compose.onNodeWithText("Name A–Z").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle {
            assertEquals(OfflinePage.FAVORITES, state.page)
            assertEquals("", state.poiQuery)
            assertEquals(PoiSort.NAME, state.sort)
            assertEquals(PoiTypes.defaults, applied)
        }
    }

    @Test fun regionGuidanceIsClickableAtFarRightEdge() {
        var page = OfflinePage.POIS
        compose.setContent { AppTheme { OfflineHomeContent(
            OfflineUiState(ready = true, page = OfflinePage.POIS, invitation = false),
            OfflineActions(page = { page = it })) } }
        val guidance = compose.onNodeWithContentDescription("Choose regions")
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertEquals(rootWidth, guidance.fetchSemanticsNode().boundsInRoot.width, 1f)
        guidance.performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, center.y)) }
        compose.runOnIdle { assertEquals(OfflinePage.REGIONS, page) }
    }

    @Test fun poiTextLayoutShowsDistanceTypeAddressAndCoordinates() {
        compose.setContent { AppTheme { OfflineHomeContent(OfflineUiState(ready = true,
            rows = listOf(
                OfflineRow(Waypoint.Discovered("one", "Example cafe",
                    Waypoint.Address("SE", "Kävlinge", null, "Main Street", "1", null, null),
                    55.6, 13.0), setOf("food.cafe"), 1200.0),
                OfflineRow(Waypoint.Discovered("two", "Unknown location distance",
                    Waypoint.Address(null, null, null, null, null, null, null),
                    55.7, 13.1), emptySet(), null))), OfflineActions()) } }
        val distance = compose.onNodeWithText("1.2km", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithText("Example cafe", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val type = compose.onNodeWithText("Cafe", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val address = compose.onNodeWithText("Main Street\u00A01, Kävlinge · 55.6000, 13.0000", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(distance.right < name.left)
        assertTrue(distance.center.y >= name.top && distance.center.y <= name.bottom)
        assertTrue(type.top >= name.bottom)
        assertTrue(address.top >= type.bottom)
        compose.onNodeWithText("55.7000, 13.1000", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("POI", useUnmergedTree = true).assertExists()
        capture("offline-poi-text-layout.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val node = if (name == "offline-filters.png") compose.onNodeWithTag("poi-filter-sheet") else compose.onAllNodes(isRoot()).onLast()
        val image = node.captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
