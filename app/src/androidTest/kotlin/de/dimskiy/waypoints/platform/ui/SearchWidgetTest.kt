package de.dimskiy.waypoints.platform.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import de.dimskiy.waypoints.DataResult
import de.dimskiy.waypoints.platform.ui.screens.waypointslist.SearchWidget
import de.dimskiy.waypoints.platform.ui.screens.waypointslist.model.SearchResponse
import de.dimskiy.waypoints.platform.ui.screens.waypointslist.model.UserIntent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchWidgetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchKeepsFocusWhenResultsChangeAndCanSubmitAgain() {
        val response = mutableStateOf(SearchResponse(DataResult.Ready(emptyList()), "Photon", false))
        val searches = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SearchWidget(response.value, { intent ->
                    if (intent is UserIntent.PerformSearch) searches += intent.query
                })
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performClick()
        input.performTextInput("coffee")
        input.assertIsFocused()
        input.performImeAction()
        compose.runOnIdle {
            assertEquals(listOf("coffee"), searches)
            response.value = response.value.copy(dataResult = DataResult.Loading())
        }
        input.assertIsFocused()
        input.assertTextContains("coffee")
        input.performTextInput(" shop")
        input.performImeAction()
        compose.runOnIdle { assertEquals(listOf("coffee", "coffee shop"), searches) }
    }
}
