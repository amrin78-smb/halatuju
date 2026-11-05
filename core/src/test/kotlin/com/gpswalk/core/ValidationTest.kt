package com.gpswalk.core

import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.RouteValidation
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.SimulationState.*
import com.gpswalk.core.model.UpdateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun coordinateRangeValidation() {
        assertTrue(Coordinate.parse("90", "180").isSuccess)
        assertTrue(Coordinate.parse("-90", "-180").isSuccess)
        assertTrue(Coordinate.parse("90.0001", "0").isFailure)
        assertTrue(Coordinate.parse("0", "180.0001").isFailure)
        assertTrue(Coordinate.parse("abc", "0").isFailure)
        assertTrue(Coordinate.parse("NaN", "0").isFailure)
        assertTrue(Coordinate.parse("0", "Infinity").isFailure)
        assertTrue(Coordinate.parse(" 13.75 ", " 100.5 ").isSuccess)
    }

    @Test(expected = IllegalArgumentException::class)
    fun coordinateConstructorRejectsInvalid() { Coordinate(91.0, 0.0) }

    @Test fun emptyAndSinglePointRoutesInvalid() {
        assertTrue(Route("x", emptyList()).validate() is RouteValidation.Invalid)
        assertTrue(Route("x", listOf(Coordinate(1.0, 1.0))).validate() is RouteValidation.Invalid)
        assertTrue(Route(" ", Route.PRESET_SHORT.waypoints).validate() is RouteValidation.Invalid)
    }

    @Test fun allDuplicateWaypointsInvalid() {
        val p = Coordinate(1.0, 1.0)
        assertTrue(Route("x", listOf(p, p, p)).validate() is RouteValidation.Invalid)
    }

    @Test fun consecutiveDuplicatesRemovedWithWarning() {
        val a = Coordinate(1.0, 1.0); val b = Coordinate(1.001, 1.0)
        val v = Route("x", listOf(a, a, b, b)).validate() as RouteValidation.Valid
        assertEquals(listOf(a, b), v.route.waypoints)
        assertEquals(1, v.warnings.size)
    }

    @Test fun presetsAreValid() {
        Route.PRESETS.forEach { assertTrue(it.name, it.validate() is RouteValidation.Valid) }
    }

    @Test fun speedLimits() {
        MovementProfile.custom(1.0); MovementProfile.custom(8.0)
        assertTrue(runCatching { MovementProfile.custom(0.9) }.isFailure)
        assertTrue(runCatching { MovementProfile.custom(8.1) }.isFailure)
        assertEquals(listOf(2.0, 4.0, 5.0, 6.0), MovementProfile.PRESETS.map { it.speedKmh })
    }

    @Test fun intervals() {
        assertEquals(listOf(500L, 1000L, 2000L, 5000L), UpdateInterval.entries.map { it.millis })
        assertEquals(UpdateInterval.ONE_SECOND, UpdateInterval.fromMillis(12345))
    }

    @Test fun stateTransitions() {
        assertTrue(IDLE.canTransitionTo(READY))
        assertFalse(IDLE.canTransitionTo(RUNNING))
        assertTrue(READY.canTransitionTo(RUNNING))
        assertTrue(RUNNING.canTransitionTo(PAUSED))
        assertTrue(RUNNING.canTransitionTo(COMPLETED))
        assertFalse(PAUSED.canTransitionTo(COMPLETED))
        assertTrue(PAUSED.canTransitionTo(RUNNING))
        assertFalse(COMPLETED.canTransitionTo(RUNNING)) // no silent restart
        assertFalse(STOPPED.canTransitionTo(RUNNING))
        assertTrue(STOPPED.canTransitionTo(READY))
        SimulationState.entries.forEach { assertFalse("$it -> IDLE->RUNNING", it == IDLE && it.canTransitionTo(PAUSED)) }
    }
}
