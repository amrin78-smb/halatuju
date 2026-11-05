package com.gpswalk.simulator

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.gpswalk.simulator.permissions.Permissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenUiTest {
    @get:Rule(order = 0) val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun idleControlsAreEnabledCorrectly() {
        compose.onNodeWithText("GPS Walk Simulator").assertIsDisplayed()
        compose.onNodeWithTag("btn_start").assertIsEnabled()
        compose.onNodeWithTag("btn_pause").assertIsNotEnabled()
        compose.onNodeWithTag("btn_resume").assertIsNotEnabled()
        compose.onNodeWithTag("btn_stop").assertIsNotEnabled()
    }

    @Test fun routeTabShowsPresets() {
        compose.onNodeWithTag("tab_Route").performClick()
        // A preset name can also appear in the editor's name field when it is the selected route.
        listOf("Route A – Short Test", "Route B – Medium Test", "Route C – Multi-Waypoint Test").forEach {
            compose.onAllNodesWithText(it).onFirst().assertIsDisplayed()
        }
    }

    @Test fun setupTabShowsMockLocationInstructions() {
        compose.onNodeWithTag("tab_Setup").performClick()
        compose.onNodeWithText("Open Developer options").assertIsDisplayed()
    }

    @Test fun permissionsRequestNoBackgroundLocation() {
        val requested = Permissions.startRequirements()
        assertFalse(requested.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        assertTrue(requested.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(Permissions.hasLocation(compose.activity))
    }
}
