package com.gpswalk.simulator

import com.gpswalk.core.model.Route
import com.gpswalk.simulator.ui.RouteDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDraftTest {
    @Test fun presetRoundTrip() {
        Route.PRESETS.forEach { r -> assertEquals(r, RouteDraft.from(r).toRoute().getOrThrow()) }
    }

    @Test fun multiWaypointDraftKeepsIntermediatePoints() {
        val d = RouteDraft.from(Route.PRESET_MULTI)
        assertEquals(2, d.waypoints.size)
        assertEquals("13.7573" to "100.5018", d.waypoints[0])
    }

    @Test fun invalidInputReportsWhichField() {
        val bad = RouteDraft.from(Route.PRESET_SHORT).copy(destLat = "95")
        val err = bad.toRoute().exceptionOrNull()!!
        assertTrue(err.message!!.startsWith("Destination"))
        val nan = RouteDraft.from(Route.PRESET_MULTI).copy(waypoints = listOf("x" to "1"))
        assertTrue(nan.toRoute().exceptionOrNull()!!.message!!.startsWith("Waypoint 1"))
    }
}
