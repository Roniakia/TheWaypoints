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
        var state by mutableStateOf(OfflineUiState(ready = true, catalogue = VerifiedCatalogue(listOf(base, halland), ""),
            installed = listOf(InstalledRegion("se-skane", "Skåne", base.descriptor.revision, 19159, base.descriptor.sha256)),
            recommended = listOf("se-halland"), selected = setOf("se-skane")))
        var updated = emptySet<String>()
        compose.setContent { AppTheme { OfflineHomeContent(state, OfflineActions(regionQuery = { state = state.copy(regionQuery = it) }, update = { updated = state.selected })) } }
        val input = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("Choose page").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(input.center.y - menu.center.y) < 3)
        assertTrue(compose.onNodeWithText("Skåne").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Halland").fetchSemanticsNode().boundsInRoot.top)
        capture("offline-regions.png")
        compose.onNode(hasSetTextAction()).performTextInput("halland")
        compose.onNodeWithText("Skåne").assertDoesNotExist()
        compose.onNodeWithText("1 ×").assertExists()
        compose.onNodeWithText("Update selected").performClick()
        compose.runOnIdle { assertEquals(setOf("se-skane"), updated) }
    }

    @Test fun nestedParentsHavePartialAndAllSelection() {
        val base = catalogue().regions.single()
        val other = base.copy(name = "Halland", searchName = "halland", descriptor = base.descriptor.copy(id = "se-halland"))
        var state by mutableStateOf(OfflineUiState(ready = true, catalogue = VerifiedCatalogue(listOf(base, other), "")))
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

    private fun capture(name: String) {
        compose.waitForIdle()
        val node = if (name == "offline-filters.png") compose.onNodeWithTag("poi-filter-sheet") else compose.onAllNodes(isRoot()).onLast()
        val image = node.captureToImage().asAndroidBitmap()
        File(context.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
