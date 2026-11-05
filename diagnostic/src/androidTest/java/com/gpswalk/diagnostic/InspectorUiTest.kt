package com.gpswalk.diagnostic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InspectorUiTest {
    @get:Rule val compose = createComposeRule()

    private fun loc(mock: Boolean) = ReceivedLocation(LocationReceiver.SOURCE_GPS, "gps", 13.7563, 100.5018, 0.0, 5f, 1.1f, 90f,
        System.currentTimeMillis(), mock, System.currentTimeMillis())

    private fun show(state: InspectorState) = compose.setContent {
        MaterialTheme { InspectorContent(state, {}, {}, {}) }
    }

    @Test fun rendersMock() {
        show(InspectorState(permissionGranted = true, locationEnabled = true, latest = loc(true)))
        compose.onNodeWithText("MOCK LOCATION RECEIVED").assertIsDisplayed()
        compose.onNodeWithText("TRUE (mock)").assertIsDisplayed()
        compose.onNodeWithText("13.756300").assertIsDisplayed()
    }

    @Test fun rendersReal() {
        show(InspectorState(permissionGranted = true, locationEnabled = true, latest = loc(false)))
        compose.onNodeWithText("REAL LOCATION RECEIVED").assertIsDisplayed()
    }

    @Test fun rendersPermissionDenied() {
        show(InspectorState(permissionGranted = false))
        compose.onNodeWithText("LOCATION PERMISSION DENIED").assertIsDisplayed()
        compose.onNodeWithText("Grant location permission").assertIsDisplayed()
    }

    @Test fun rendersNoLocation() {
        show(InspectorState(permissionGranted = true, locationEnabled = true))
        compose.onNodeWithText("NO LOCATION RECEIVED").assertIsDisplayed()
    }
}
