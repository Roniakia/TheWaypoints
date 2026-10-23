package de.dimskiy.waypoints.platform.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.dimskiy.waypoints.platform.offline.*
import de.dimskiy.waypoints.platform.ui.screens.*
import de.dimskiy.waypoints.platform.ui.theme.AppTheme
import de.dimskiy.waypoints.platform.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun settingsNavigationChannelAndManualCheck() {
        var page by mutableStateOf(OfflinePage.POIS)
        var updates by mutableStateOf(UpdateUiState())
        var checked = 0
        compose.setContent { AppTheme {
            OfflineHomeContent(OfflineUiState(ready = true, page = page), OfflineActions(page = { page = it })) {
                AppUpdateSettingsContent(updates, channel = { updates = updates.copy(channel = it) }, check = { checked++ })
            }
        } }
        compose.onNodeWithContentDescription("Choose page").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("App updates").assertExists()
        compose.onNodeWithContentDescription("Filters").assertDoesNotExist()
        compose.onNodeWithText("Stable", substring = false).assertIsSelected()
        compose.onNodeWithText("Beta", substring = false).performClick()
        compose.onNodeWithText("Beta", substring = false).assertIsSelected()
        compose.onNodeWithText("Stable", substring = false).assertIsNotSelected()
        val floating = compose.onNodeWithText("Check for updates").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Not checked yet.").performScrollTo()
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
        assertEquals(floating, compose.onNodeWithText("Check for updates").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Check for updates").performClick()
        compose.runOnIdle { assertEquals(UpdateChannel.BETA, updates.channel); assertEquals(1, checked) }
    }
    @Test fun updateBannerOpensSettingsAndBusyCheckDisablesChannels() {
        var page by mutableStateOf(OfflinePage.FAVORITES)
        val release = AppRelease("0.97", 9, "v0.97-beta.1", "New features", "https://example.invalid/app.apk", 1_000_000, "ab".repeat(32))
        compose.setContent { AppTheme {
            OfflineHomeContent(OfflineUiState(ready = true, page = page), OfflineActions(page = { page = it }), UpdateUiState(release = release)) {
                AppUpdateSettingsContent(UpdateUiState(checking = true))
            }
        } }
        compose.onNodeWithText("Update available · 0.97").performClick()
        compose.onNodeWithText("Stable", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Beta", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Checking…").assertIsNotEnabled()
    }
    @Test fun bottomActionFollowsSelectedChannelDownloadAndInstallState() {
        val release = AppRelease("0.99", 11, "v0.99-beta.1", "Notes", "https://example.invalid/a.apk", 3, "ab".repeat(32))
        var state by mutableStateOf(UpdateUiState())
        var checks = 0
        var downloads = 0
        var installs = 0
        var cancels = 0
        compose.setContent { AppTheme { AppUpdateSettingsContent(state,
            check = { checks++ }, download = { downloads++ }, install = { installs++ },
            cancel = { cancels++ }, canInstallUpdates = true) } }
        compose.onNodeWithText("Check for updates").performClick()
        compose.runOnIdle { assertEquals(1, checks); state = state.copy(channel = UpdateChannel.BETA, release = release) }
        val action = compose.onNodeWithText("Download and install")
        val bounds = action.fetchSemanticsNode().boundsInRoot
        action.performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
        compose.onNodeWithText("Download update").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(downloading = true, progress = 42) }
        compose.onNodeWithText("Downloading… 42%").assertIsNotEnabled()
        compose.onNodeWithText("Cancel download").performScrollTo()
        // Scroll through the trailing padding so the fixed bottom action clears Cancel.
        compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
        compose.onNodeWithText("Cancel download").performClick()
        compose.runOnIdle { assertEquals(1, cancels); state = state.copy(downloading = false, apk = java.io.File("/unused/update.apk")) }
        compose.onNodeWithText("Install update").assertIsDisplayed().performClick()
        assertEquals(bounds, compose.onNodeWithText("Install update").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, installs); state = state.copy(channel = UpdateChannel.STABLE, release = null, apk = null) }
        compose.onNodeWithText("Check for updates").assertIsDisplayed()
        compose.onNodeWithText("Install update").assertDoesNotExist()
    }

}
