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
}
