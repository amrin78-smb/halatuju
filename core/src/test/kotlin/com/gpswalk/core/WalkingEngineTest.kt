package com.gpswalk.core

import com.gpswalk.core.engine.DistanceCalculator
import com.gpswalk.core.engine.RouteInterpolator
import com.gpswalk.core.engine.WalkingEngine
import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.SimulationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingEngineTest {
    private val time = FakeTime()
    // 3.6 km/h = exactly 1 m/s, simplifies expectations.
    private val oneMps = MovementProfile.custom(3.6)

    private fun engine(route: Route = Route.PRESET_MEDIUM) = WalkingEngine(route, oneMps, time)

    @Test fun interpolatorFollowsSegmentsAndBearings() {
        val interp = RouteInterpolator(Route.PRESET_MULTI)
        val seg1 = DistanceCalculator.haversineMeters(Route.PRESET_MULTI.waypoints[0], Route.PRESET_MULTI.waypoints[1])
        val p = interp.positionAt(seg1 / 2)
        assertEquals(0, p.segmentIndex)
        assertEquals(0.0, p.bearingDegrees, 0.01) // heading north
        assertEquals(100.5018, p.coordinate.longitude, 1e-9)
        val q = interp.positionAt(seg1 + 10)
        assertEquals(1, q.segmentIndex)
        assertEquals(90.0, q.bearingDegrees, 0.05) // heading east
        val end = interp.positionAt(1e9)
        assertTrue(end.isAtEnd)
        assertEquals(Route.PRESET_MULTI.destination, end.coordinate)
        assertEquals(180.0, end.bearingDegrees, 0.05)
    }

    @Test fun interpolatorEveryPointOnRouteIsCloseToPath() {
        val interp = RouteInterpolator(Route.PRESET_MEDIUM)
        var d = 0.0
        while (d < interp.totalDistanceMeters) {
            val c = interp.positionAt(d).coordinate
            assertEquals(13.7563, c.latitude, 1e-5) // great circle E-W deviation is tiny at 1 km
            d += 50
        }
    }

    @Test fun shortAndLongSegments() {
        val tiny = Route("tiny", listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 0.00001))) // ~1.1 m
        val e = WalkingEngine(tiny, oneMps, time); e.start()
        time.advanceMillis(5_000)
        val s = e.sample()
        assertEquals(SimulationState.COMPLETED, s.state)
        assertEquals(tiny.destination!!.longitude, s.fix.longitude, 1e-12)

        val long = Route("long", listOf(Coordinate(0.0, 0.0), Coordinate(10.0, 10.0)))
        val interp = RouteInterpolator(long)
        assertTrue(interp.totalDistanceMeters > 1_500_000)
        val mid = interp.positionAt(interp.totalDistanceMeters / 2).coordinate
        assertEquals(interp.totalDistanceMeters / 2, DistanceCalculator.haversineMeters(long.start!!, mid), 1.0)
    }

    @Test fun distanceAndElapsedTimeFollowMonotonicClock() {
        val e = engine(); e.start()
        time.advanceMillis(10_000)
        val s = e.sample()
        assertEquals(10.0, s.distanceTravelledMeters, 1e-6)
        assertEquals(10_000_000_000L, s.movingElapsedNanos)
        assertEquals(1f, s.fix.speedMetersPerSecond, 1e-6f)
        assertEquals(90f, s.fix.bearingDegrees, 0.05f)
        assertEquals(e.totalDistanceMeters - 10.0, s.remainingMeters, 1e-6)
    }

    @Test fun irregularTimerTicksDoNotAffectPosition() {
        val e = engine(); e.start()
        listOf(300L, 1700L, 50L, 2950L).forEach { time.advanceMillis(it); e.sample() }
        assertEquals(5.0, e.sample().distanceTravelledMeters, 1e-6)
    }

    @Test fun pauseFreezesPositionAndResumeContinuesWithoutJump() {
        val e = engine(); e.start()
        time.advanceMillis(20_000)
        e.pause()
        val atPause = e.sample()
        assertEquals(SimulationState.PAUSED, atPause.state)
        assertEquals(0f, atPause.fix.speedMetersPerSecond)
        time.advanceMillis(60_000) // long pause
        val stillPaused = e.sample()
        assertEquals(atPause.fix.latitude, stillPaused.fix.latitude, 0.0)
        assertEquals(atPause.fix.longitude, stillPaused.fix.longitude, 0.0)
        assertEquals(20_000_000_000L, stillPaused.movingElapsedNanos)
        e.resume()
        val justResumed = e.sample()
        assertEquals(20.0, justResumed.distanceTravelledMeters, 1e-6) // no jump
        time.advanceMillis(5_000)
        assertEquals(25.0, e.sample().distanceTravelledMeters, 1e-6)
    }

    @Test fun speedChangeBanksProgress() {
        val e = engine(); e.start()
        time.advanceMillis(10_000)
        e.setProfile(MovementProfile.custom(7.2)) // 2 m/s
        time.advanceMillis(10_000)
        assertEquals(30.0, e.distanceTravelledMeters(), 1e-6)
    }

    @Test fun completionStopsAtDestinationWithExactTime() {
        val e = engine(Route.PRESET_SHORT); e.start()
        val total = e.totalDistanceMeters
        time.advanceMillis(1_000_000)
        val s = e.sample()
        assertEquals(SimulationState.COMPLETED, s.state)
        assertEquals(total, s.distanceTravelledMeters, 1e-9)
        assertEquals(0.0, s.remainingMeters, 1e-9)
        assertEquals(Route.PRESET_SHORT.destination!!.latitude, s.fix.latitude, 1e-12)
        assertEquals(0f, s.fix.speedMetersPerSecond)
        assertEquals(total, s.movingElapsedNanos / 1e9, 1e-6) // 1 m/s
        // Further time does not change anything; no restart.
        time.advanceMillis(10_000)
        assertEquals(SimulationState.COMPLETED, e.sample().state)
        assertTrue(runCatching { e.start() }.isFailure)
    }

    @Test fun invalidTransitionsRejected() {
        val e = engine()
        assertTrue(runCatching { e.pause() }.isFailure)
        assertTrue(runCatching { e.resume() }.isFailure)
        assertTrue(runCatching { e.stop() }.isFailure)
        e.start()
        assertTrue(runCatching { e.start() }.isFailure)
        assertTrue(runCatching { e.resume() }.isFailure)
        e.stop()
        assertEquals(SimulationState.STOPPED, e.state)
        assertTrue(runCatching { e.resume() }.isFailure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun interpolatorRejectsDuplicateSegment() {
        val p = Coordinate(1.0, 1.0)
        RouteInterpolator(Route("dup", listOf(p, p)))
    }
}
