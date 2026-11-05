package com.gpswalk.simulator

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import com.gpswalk.simulator.location.MockLocationStatus
import com.gpswalk.simulator.service.WalkingSimulationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceLifecycleTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
    )
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val controller get() = context.gpsWalkApp.controller

    private fun waitFor(state: SimulationState, timeoutMs: Long = 10_000) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            var s: SimulationState? = null
            instrumentation.runOnMainSync { s = controller.state }
            if (s == state) return
            Thread.sleep(100)
        }
        var s: SimulationState? = null
        instrumentation.runOnMainSync { s = controller.state }
        throw AssertionError("Expected $state, was $s (last error: ${controller.snapshot.value.lastError})")
    }

    @Test fun startPauseResumeStopThroughService() {
        assumeTrue("App must be selected as mock location app", MockLocationStatus.isSelectedMockLocationApp(context))
        instrumentation.runOnMainSync {
            assertNull(controller.load(Route.PRESET_MEDIUM, MovementProfile.FAST, UpdateInterval.HALF_SECOND, useFused = false))
            WalkingSimulationService.start(context)
        }
        waitFor(SimulationState.RUNNING)
        Thread.sleep(1_500)
        instrumentation.runOnMainSync { controller.pause() }
        waitFor(SimulationState.PAUSED)
        var d1 = 0.0
        instrumentation.runOnMainSync { d1 = controller.snapshot.value.distanceTravelledMeters }
        Thread.sleep(1_500)
        instrumentation.runOnMainSync { assertEquals(d1, controller.snapshot.value.distanceTravelledMeters, 0.0) }
        instrumentation.runOnMainSync { controller.resume() }
        waitFor(SimulationState.RUNNING)
        // Duplicate start request while active must be ignored.
        instrumentation.runOnMainSync { WalkingSimulationService.start(context) }
        Thread.sleep(500)
        waitFor(SimulationState.RUNNING)
        instrumentation.runOnMainSync { controller.stop() }
        waitFor(SimulationState.STOPPED)
    }

    @Test fun startFailsCleanlyWithoutMockSelection() {
        assumeTrue(!MockLocationStatus.isSelectedMockLocationApp(context))
        instrumentation.runOnMainSync {
            assertNull(controller.load(Route.PRESET_SHORT, MovementProfile.NORMAL, UpdateInterval.ONE_SECOND, useFused = false))
            WalkingSimulationService.start(context)
        }
        waitFor(SimulationState.ERROR)
    }
}
