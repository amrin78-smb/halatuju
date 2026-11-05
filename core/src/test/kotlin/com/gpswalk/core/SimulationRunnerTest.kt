package com.gpswalk.core

import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.engine.SimulationRunner
import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationRunnerTest {
    private val oneMps = MovementProfile.custom(3.6)

    private fun TestScope.runner(pub: FakePublisher) =
        SimulationRunner(pub, SchedulerTime(testScheduler), backgroundScope)

    @Test fun publishesAtIntervalAndProgresses() = runTest {
        val pub = FakePublisher()
        val r = runner(pub)
        assertNull(r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.ONE_SECOND))
        assertEquals(SimulationState.READY, r.state)
        assertTrue(r.start())
        runCurrent()
        assertEquals(SimulationState.RUNNING, r.state)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(11, pub.published.size) // t=0..10s
        assertEquals(10.0, r.snapshot.value.distanceTravelledMeters, 1e-6)
        assertEquals(11, r.snapshot.value.publishedCount)
        assertEquals(true, r.snapshot.value.lastInjectionOk)
        // Longitudes strictly increase (walking east).
        assertTrue(pub.published.zipWithNext().all { (a, b) -> b.longitude > a.longitude })
        r.stop(); runCurrent()
    }

    @Test fun halfSecondInterval() = runTest {
        val pub = FakePublisher()
        val r = runner(pub)
        r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.HALF_SECOND); r.start(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(5, pub.published.size)
        r.stop(); runCurrent()
    }

    @Test fun pauseResumeStop() = runTest {
        val pub = FakePublisher()
        val r = runner(pub)
        r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertTrue(r.pause())
        assertEquals(SimulationState.PAUSED, r.state)
        val pausedAt = r.snapshot.value.distanceTravelledMeters
        advanceTimeBy(30_000); runCurrent()
        assertEquals(pausedAt, r.snapshot.value.distanceTravelledMeters, 0.0)
        // Held position keeps being published with zero speed.
        assertEquals(0f, pub.published.last().speedMetersPerSecond)
        assertTrue(r.resume())
        advanceTimeBy(3_000); runCurrent()
        assertEquals(pausedAt + 3.0, r.snapshot.value.distanceTravelledMeters, 1e-6)
        val countBeforeStop = pub.published.size
        assertTrue(r.stop()); runCurrent()
        assertEquals(SimulationState.STOPPED, r.state)
        assertEquals(1, pub.stopCalls)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(countBeforeStop, pub.published.size) // nothing after stop
        assertFalse(r.resume())
        assertFalse(r.start()) // must reload; no silent restart
    }

    @Test fun completesRouteAndReleasesProvider() = runTest {
        val pub = FakePublisher()
        val r = runner(pub)
        r.load(Route.PRESET_SHORT, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        val total = r.snapshot.value.totalDistanceMeters
        advanceTimeBy(200_000); runCurrent()
        val s = r.snapshot.value
        assertEquals(SimulationState.COMPLETED, s.state)
        assertEquals(total, s.distanceTravelledMeters, 1e-9)
        assertEquals(0.0, s.remainingMeters, 1e-9)
        assertEquals(total * 1000, s.movingElapsedMillis.toDouble(), 1.0)
        val last = pub.published.last()
        assertEquals(Route.PRESET_SHORT.destination!!.latitude, last.latitude, 1e-12)
        assertEquals(1, pub.stopCalls)
        val count = pub.published.size
        advanceTimeBy(10_000); runCurrent()
        assertEquals(count, pub.published.size)
    }

    @Test fun initializationFailureGoesToError() = runTest {
        val pub = FakePublisher(initResult = PublishResult.Failure("not mock app", fatal = true))
        val r = runner(pub)
        r.load(Route.PRESET_SHORT, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        assertEquals(SimulationState.ERROR, r.state)
        assertEquals("not mock app", r.snapshot.value.lastError)
        assertEquals(0, pub.published.size)
        assertEquals(false, r.snapshot.value.lastInjectionOk)
    }

    @Test fun fatalPublishFailureStopsImmediately() = runTest {
        val pub = FakePublisher(failWith = PublishResult.Failure("SecurityException", fatal = true))
        val r = runner(pub)
        r.load(Route.PRESET_SHORT, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        assertEquals(SimulationState.ERROR, r.state)
        assertEquals(0, r.snapshot.value.publishedCount)
        assertEquals(1, pub.stopCalls)
    }

    @Test fun nonFatalFailuresAreBounded() = runTest {
        val pub = FakePublisher(failWith = PublishResult.Failure("transient", fatal = false))
        val r = runner(pub)
        r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(SimulationState.RUNNING, r.state) // still retrying
        advanceTimeBy(10_000); runCurrent()
        assertEquals(SimulationState.ERROR, r.state)
        assertNotNull(r.snapshot.value.lastError)
    }

    @Test fun invalidRouteRejected() = runTest {
        val r = runner(FakePublisher())
        assertNotNull(r.load(Route("bad", listOf(Coordinate(1.0, 1.0))), oneMps, UpdateInterval.ONE_SECOND))
        assertEquals(SimulationState.IDLE, r.state)
        assertFalse(r.start())
    }

    @Test fun cannotLoadWhileActiveOrStartTwice() = runTest {
        val r = runner(FakePublisher())
        r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.ONE_SECOND)
        assertTrue(r.start()); assertFalse(r.start()); runCurrent()
        assertNotNull(r.load(Route.PRESET_SHORT, oneMps, UpdateInterval.ONE_SECOND))
        r.stop(); runCurrent()
        assertNull(r.load(Route.PRESET_SHORT, oneMps, UpdateInterval.ONE_SECOND))
    }

    @Test fun externalFailureReported() = runTest {
        val pub = FakePublisher()
        val r = runner(pub)
        r.load(Route.PRESET_MEDIUM, oneMps, UpdateInterval.ONE_SECOND); r.start(); runCurrent()
        r.failExternally("Service killed"); runCurrent()
        assertEquals(SimulationState.ERROR, r.state)
        assertEquals("Service killed", r.snapshot.value.lastError)
        assertEquals(1, pub.stopCalls)
    }
}
