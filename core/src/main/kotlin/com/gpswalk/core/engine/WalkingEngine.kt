package com.gpswalk.core.engine

import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.core.model.SimulationState
import kotlin.math.min

/**
 * Deterministic walking model. Position is a pure function of accumulated moving time and
 * speed, measured with a monotonic clock — never by counting timer ticks — so late timer
 * callbacks, pause/resume and speed changes cannot cause jumps or drift.
 *
 * Not thread-safe: confine calls to one thread/dispatcher.
 */
class WalkingEngine(
    route: Route,
    profile: MovementProfile,
    private val time: TimeSource,
    private val accuracyMeters: Float = DEFAULT_ACCURACY_METERS,
) {
    val interpolator = RouteInterpolator(route)
    val route: Route get() = interpolator.route
    val totalDistanceMeters: Double get() = interpolator.totalDistanceMeters

    var profile: MovementProfile = profile
        private set

    var state: SimulationState = SimulationState.READY
        private set

    /** Distance banked from completed running intervals. */
    private var bankedMeters = 0.0
    private var bankedMovingNanos = 0L
    /** Monotonic timestamp when the current running interval began; null when not running. */
    private var runningSinceNanos: Long? = null

    fun start() {
        transition(SimulationState.RUNNING, requireFrom = SimulationState.READY)
        runningSinceNanos = time.monotonicNanos()
    }

    fun pause() {
        bank(time.monotonicNanos())
        transition(SimulationState.PAUSED, requireFrom = SimulationState.RUNNING)
    }

    fun resume() {
        transition(SimulationState.RUNNING, requireFrom = SimulationState.PAUSED)
        runningSinceNanos = time.monotonicNanos()
    }

    fun stop() {
        check(state.isActive) { "Cannot stop from $state" }
        if (state == SimulationState.RUNNING) bank(time.monotonicNanos())
        state = SimulationState.STOPPED
    }

    fun fail() {
        if (state == SimulationState.RUNNING) bank(time.monotonicNanos())
        check(state.canTransitionTo(SimulationState.ERROR)) { "Cannot enter ERROR from $state" }
        state = SimulationState.ERROR
    }

    /** Changes speed without moving the walker: progress so far is banked at the old speed. */
    fun setProfile(newProfile: MovementProfile) {
        if (state == SimulationState.RUNNING) {
            val now = time.monotonicNanos()
            bank(now)
            runningSinceNanos = now
        }
        profile = newProfile
    }

    fun distanceTravelledMeters(): Double = progressAt(time.monotonicNanos()).first

    fun movingElapsedNanos(): Long = progressAt(time.monotonicNanos()).second

    /**
     * Computes the current position. If the walker reaches the destination, the engine
     * transitions to [SimulationState.COMPLETED] and the returned fix is the destination.
     */
    fun sample(): EngineSample {
        val now = time.monotonicNanos()
        val (distance, movingNanos) = progressAt(now)
        if (state == SimulationState.RUNNING && distance >= totalDistanceMeters) {
            bankedMeters = totalDistanceMeters
            bankedMovingNanos = movingNanos
            runningSinceNanos = null
            state = SimulationState.COMPLETED
        }
        val position = interpolator.positionAt(distance)
        val moving = state == SimulationState.RUNNING
        val fix = SimulatedFix(
            latitude = position.coordinate.latitude,
            longitude = position.coordinate.longitude,
            altitudeMeters = position.coordinate.altitudeMeters,
            accuracyMeters = accuracyMeters,
            bearingDegrees = position.bearingDegrees.toFloat(),
            speedMetersPerSecond = if (moving) profile.speedMetersPerSecond.toFloat() else 0f,
            timeMillis = time.wallClockMillis(),
            elapsedRealtimeNanos = now,
        )
        return EngineSample(
            fix = fix,
            state = state,
            distanceTravelledMeters = distance,
            remainingMeters = (totalDistanceMeters - distance).coerceAtLeast(0.0),
            movingElapsedNanos = movingNanos,
            segmentIndex = position.segmentIndex,
        )
    }

    /** Returns (distance, movingNanos) at [now], capped at the route end. */
    private fun progressAt(now: Long): Pair<Double, Long> {
        val since = runningSinceNanos
        if (state != SimulationState.RUNNING || since == null) return bankedMeters to bankedMovingNanos
        val speed = profile.speedMetersPerSecond
        val intervalNanos = (now - since).coerceAtLeast(0L)
        val remaining = totalDistanceMeters - bankedMeters
        val nanosToFinish = (remaining / speed * 1e9).toLong()
        val effectiveNanos = min(intervalNanos, nanosToFinish)
        val distance = if (intervalNanos >= nanosToFinish) {
            totalDistanceMeters
        } else {
            bankedMeters + speed * effectiveNanos / 1e9
        }
        return distance to (bankedMovingNanos + effectiveNanos)
    }

    private fun bank(now: Long) {
        val (distance, moving) = progressAt(now)
        bankedMeters = distance
        bankedMovingNanos = moving
        runningSinceNanos = null
    }

    private fun transition(next: SimulationState, requireFrom: SimulationState) {
        check(state == requireFrom && state.canTransitionTo(next)) { "Invalid transition $state -> $next" }
        state = next
    }

    companion object {
        const val DEFAULT_ACCURACY_METERS = 5f
    }
}

data class EngineSample(
    val fix: SimulatedFix,
    val state: SimulationState,
    val distanceTravelledMeters: Double,
    val remainingMeters: Double,
    val movingElapsedNanos: Long,
    val segmentIndex: Int,
)
