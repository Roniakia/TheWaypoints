package de.dimskiy.waypoints.platform.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class OfflineLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesRealAppDownloadsAndSearchesSkane() {
        assumeTrue("Opt in to GitHub networking with offlineLive=true", InstrumentationRegistry.getArguments().getString("offlineLive") == "true")
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Choose page").performClick()
        compose.onNodeWithText("Regions", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("skane")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Select Skåne").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Select Skåne").performScrollTo().performClick()
        compose.onNodeWithText("Update selected · 1").performClick()
        compose.waitUntil(90_000) { compose.onAllNodesWithText("Downloaded · 2026-10-20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Choose page").performClick()
        compose.onNodeWithText("POIs", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("water")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Bookmark Drinking water").fetchSemanticsNodes().isNotEmpty() }
        val image = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "offline-live-search.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
