package com.gpswalk.core.engine

import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.RouteValidation
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Everything the UI / notification needs to render the simulation. */
data class SimulationSnapshot(
    val state: SimulationState = SimulationState.IDLE,
    val routeName: String = "",
    val route: Route? = null,
    val totalDistanceMeters: Double = 0.0,
    val distanceTravelledMeters: Double = 0.0,
    val remainingMeters: Double = 0.0,
    val movingElapsedMillis: Long = 0,
    val speedKmh: Double = MovementProfile.NORMAL.speedKmh,
    val intervalMillis: Long = UpdateInterval.DEFAULT.millis,
    val currentFix: SimulatedFix? = null,
    val publishedCount: Long = 0,
    val lastPublishWallMillis: Long? = null,
    val lastInjectionResult: String = "Not started",
    val lastInjectionOk: Boolean? = null,
    val lastError: String? = null,
    val warnings: List<String> = emptyList(),
    val publisherName: String = "",
) {
    val progressFraction: Double
        get() = if (totalDistanceMeters > 0) (distanceTravelledMeters / totalDistanceMeters).coerceIn(0.0, 1.0) else 0.0
}

/**
 * Drives a [WalkingEngine] on a coroutine loop and pushes fixes to a [LocationPublisher].
 *
 * All public methods and the loop must run on the same single-threaded dispatcher
 * (Dispatchers.Main in the app, a test dispatcher in tests); this avoids locking.
 *
 * Failure policy: a fatal publish failure (e.g. app no longer the selected mock location
 * app) moves to ERROR immediately; non-fatal failures move to ERROR after
 * [MAX_CONSECUTIVE_FAILURES] consecutive attempts. There is no indefinite retry.
 */
class SimulationRunner(
    private val publisher: LocationPublisher,
    private val time: TimeSource,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private val _snapshot = MutableStateFlow(SimulationSnapshot(publisherName = publisher.name))
    val snapshot: StateFlow<SimulationSnapshot> = _snapshot.asStateFlow()

    private var engine: WalkingEngine? = null
    private var interval: UpdateInterval = UpdateInterval.DEFAULT
    private var loopJob: Job? = null
    private var consecutiveFailures = 0
    private var publisherInitialized = false

    val state: SimulationState get() = _snapshot.value.state

    /** Loads a route. Allowed when not running/paused. Returns an error message on failure. */
    fun load(route: Route, profile: MovementProfile, interval: UpdateInterval): String? {
        if (state.isActive) return "Stop the current simulation before loading a new route"
        val valid = when (val v = route.validate()) {
            is RouteValidation.Invalid -> return v.reason
            is RouteValidation.Valid -> v
        }
        val newEngine = WalkingEngine(valid.route, profile, time)
        engine = newEngine
        this.interval = interval
        consecutiveFailures = 0
        _snapshot.value = SimulationSnapshot(
            state = SimulationState.READY,
            routeName = valid.route.name,
            route = valid.route,
            totalDistanceMeters = newEngine.totalDistanceMeters,
            remainingMeters = newEngine.totalDistanceMeters,
            speedKmh = profile.speedKmh,
            intervalMillis = interval.millis,
            warnings = valid.warnings,
            publisherName = publisher.name,
            lastInjectionResult = "Not started",
        )
        return null
    }

    /** Starts the simulation. Initializes the publisher first; failure moves to ERROR. */
    fun start(): Boolean {
        val e = engine ?: return false
        if (state != SimulationState.READY || loopJob?.isActive == true) return false
        loopJob = scope.launch {
            val init = publisher.initialize()
            if (init is PublishResult.Failure) {
                log("Publisher initialization failed: ${init.message}")
                e.fail()
                publisher.stop()
                _snapshot.update {
                    it.copy(
                        state = SimulationState.ERROR,
                        lastError = init.message,
                        lastInjectionResult = "Initialization failed",
                        lastInjectionOk = false,
                    )
                }
                return@launch
            }
            publisherInitialized = true
            e.start()
            _snapshot.update { it.copy(state = SimulationState.RUNNING, lastError = null) }
            runLoop(e)
        }
        return true
    }

    fun pause(): Boolean {
        val e = engine ?: return false
        if (state != SimulationState.RUNNING || e.state != SimulationState.RUNNING) return false
        e.pause()
        // The loop keeps publishing the held position (speed 0) so consumers do not go stale.
        applySample(e.sample())
        return true
    }

    fun resume(): Boolean {
        val e = engine ?: return false
        if (state != SimulationState.PAUSED) return false
        e.resume()
        applySample(e.sample())
        return true
    }

    /** Stops immediately and releases the provider. Returns false if nothing was active. */
    fun stop(): Boolean {
        val e = engine
        loopJob?.cancel()
        loopJob = null
        if (e == null || !state.isActive) return false
        e.stop()
        applySample(e.sample())
        releasePublisher()
        log("Simulation stopped by user")
        return true
    }

    fun setProfile(profile: MovementProfile) {
        engine?.setProfile(profile)
        _snapshot.update { it.copy(speedKmh = profile.speedKmh) }
    }

    /** Marks the simulation as failed for an external reason (e.g. service killed). */
    fun failExternally(message: String) {
        loopJob?.cancel()
        loopJob = null
        val e = engine
        if (e != null && e.state.canTransitionTo(SimulationState.ERROR)) e.fail()
        _snapshot.update { it.copy(state = SimulationState.ERROR, lastError = message) }
        releasePublisher()
    }

    private suspend fun runLoop(e: WalkingEngine) {
        while (true) {
            val sample = e.sample()
            val result = publisher.publish(sample.fix)
            // stop()/failExternally() may have run while publish() was suspended.
            currentCoroutineContext().ensureActive()
            applySample(sample)
            recordResult(result)

            when {
                result is PublishResult.Failure && (result.fatal || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) -> {
                    log("Injection failed (fatal=${result.fatal}, consecutive=$consecutiveFailures): ${result.message}")
                    e.fail()
                    _snapshot.update { it.copy(state = SimulationState.ERROR, lastError = result.message) }
                    releasePublisher()
                    return
                }
                sample.state == SimulationState.COMPLETED -> {
                    log("Route completed: %.1f m in %d ms".format(sample.distanceTravelledMeters, sample.movingElapsedNanos / 1_000_000))
                    releasePublisher()
                    return
                }
            }
            delay(interval.millis)
        }
    }

    private fun applySample(sample: EngineSample) {
        _snapshot.update {
            it.copy(
                state = sample.state,
                currentFix = sample.fix,
                distanceTravelledMeters = sample.distanceTravelledMeters,
                remainingMeters = sample.remainingMeters,
                movingElapsedMillis = sample.movingElapsedNanos / 1_000_000,
                speedKmh = engine?.profile?.speedKmh ?: it.speedKmh,
            )
        }
    }

    private fun recordResult(result: PublishResult) {
        val now = time.wallClockMillis()
        when (result) {
            is PublishResult.Success -> {
                consecutiveFailures = 0
                _snapshot.update {
                    it.copy(
                        publishedCount = it.publishedCount + 1,
                        lastPublishWallMillis = now,
                        lastInjectionOk = true,
                        lastInjectionResult = if (result.detail.isEmpty()) "OK" else "OK – ${result.detail}",
                    )
                }
            }
            is PublishResult.Failure -> {
                consecutiveFailures++
                _snapshot.update {
                    it.copy(
                        lastInjectionOk = false,
                        lastInjectionResult = "FAILED – ${result.message}",
                        lastError = result.message,
                    )
                }
            }
        }
    }

    private fun releasePublisher() {
        if (!publisherInitialized) return
        publisherInitialized = false
        scope.launch { publisher.stop() }
    }

    companion object {
        const val MAX_CONSECUTIVE_FAILURES = 5
    }
}
