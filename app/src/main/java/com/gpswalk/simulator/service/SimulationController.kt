package com.gpswalk.simulator.service

import android.content.Context
import android.os.SystemClock
import com.gpswalk.core.engine.LocationPublisher
import com.gpswalk.core.engine.SimulationRunner
import com.gpswalk.core.engine.SimulationSnapshot
import com.gpswalk.core.engine.TimeSource
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import com.gpswalk.simulator.data.SettingsRepository
import com.gpswalk.simulator.data.SimulationRecord
import com.gpswalk.simulator.location.AndroidMockLocationAdapter
import com.gpswalk.simulator.location.CompositeLocationPublisher
import com.gpswalk.simulator.location.FusedMockLocationAdapter
import com.gpswalk.simulator.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Android clocks: monotonic time matches Location.elapsedRealtimeNanos semantics. */
object AndroidTimeSource : TimeSource {
    override fun monotonicNanos(): Long = SystemClock.elapsedRealtimeNanos()
    override fun wallClockMillis(): Long = System.currentTimeMillis()
}

/**
 * Process-wide owner of the simulation. The UI and the foreground service both talk to this
 * object, so the simulation survives Activity recreation; the service only keeps the process
 * in the foreground and mirrors state into the notification.
 *
 * All methods must be called on the main thread (the scope uses Dispatchers.Main.immediate).
 */
class SimulationController(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val publisherFactory: (useFused: Boolean) -> LocationPublisher = { useFused ->
        val primary = AndroidMockLocationAdapter(context)
        if (useFused) CompositeLocationPublisher(primary, listOf(FusedMockLocationAdapter(context)))
        else CompositeLocationPublisher(primary, emptyList())
    },
) {
    private val runnerFlow = MutableStateFlow(newRunner(useFused = true))
    private val runner: SimulationRunner get() = runnerFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: StateFlow<SimulationSnapshot> =
        runnerFlow.flatMapLatest { it.snapshot }.stateIn(scope, SharingStarted.Eagerly, SimulationSnapshot())

    private val _notice = MutableStateFlow<String?>(null)
    /** One-off message, e.g. that a previous simulation was terminated by the system. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        scope.launch { detectUnexpectedTermination() }
        scope.launch { persistRecords() }
    }

    val state: SimulationState get() = snapshot.value.state

    /** Prepares a route. Returns an error message, or null on success. */
    fun load(route: Route, profile: MovementProfile, interval: UpdateInterval, useFused: Boolean): String? {
        if (runner.state.isActive) return "A simulation is already running. Stop it first."
        val fresh = newRunner(useFused)
        val error = fresh.load(route, profile, interval)
        if (error != null) {
            AppLog.w("Route rejected: $error")
            return error
        }
        runnerFlow.value = fresh
        AppLog.i("Route loaded: ${route.name}, ${"%.1f".format(fresh.snapshot.value.totalDistanceMeters)} m, " +
            "${profile.speedKmh} km/h, ${interval.millis} ms")
        return null
    }

    /** Called by the service after it has successfully entered the foreground. */
    internal fun startRunner(): Boolean = runner.start().also { AppLog.i("Runner start requested: $it") }

    fun pause(): Boolean = runner.pause().also { if (it) AppLog.i("Paused") }
    fun resume(): Boolean = runner.resume().also { if (it) AppLog.i("Resumed") }
    fun stop(): Boolean = runner.stop()

    fun setSpeed(profile: MovementProfile) = runner.setProfile(profile)

    /** Records a failure that happened outside the runner (service start/termination). */
    fun reportFailure(message: String) {
        AppLog.e(message)
        runner.failExternally(message)
    }

    fun dismissNotice() { _notice.value = null }

    private fun newRunner(useFused: Boolean) = SimulationRunner(
        publisher = publisherFactory(useFused),
        time = AndroidTimeSource,
        scope = scope,
        log = AppLog::i,
    )

    /**
     * If the stored record says a simulation was active but this is a fresh process, the
     * previous process was killed (e.g. by battery management). Report it once; do not resume.
     */
    private suspend fun detectUnexpectedTermination() {
        // A fresh process never owns an active simulation, so any test provider registered by
        // this package is stale. Removing it restores real location for every app.
        if (!state.isActive) AndroidMockLocationAdapter(context).clearLeftoverTestProviders()
        val record = settings.currentRecord() ?: return
        if (record.state.isActive && !state.isActive) {
            FusedMockLocationAdapter(context).forceDisableMockMode()
            val msg = "The previous simulation (“${record.routeName}”) stopped unexpectedly: the app process " +
                "was terminated while it was ${record.state.name.lowercase()}. It was not restarted."
            AppLog.w(msg)
            _notice.value = msg
            settings.saveRecord(record.copy(state = SimulationState.ERROR, message = msg, updatedAtMillis = System.currentTimeMillis()))
        }
    }

    /** Persists aggregate statistics on every state change and every ~10 s of progress. */
    private suspend fun persistRecords() {
        snapshot
            .map { s -> Triple(s.state, (s.movingElapsedMillis / 10_000), s.routeName) }
            .distinctUntilChanged()
            .collect {
                val s = snapshot.value
                if (s.state == SimulationState.IDLE || s.state == SimulationState.READY) return@collect
                settings.saveRecord(
                    SimulationRecord(
                        state = s.state,
                        routeName = s.routeName,
                        distanceMeters = s.distanceTravelledMeters,
                        totalMeters = s.totalDistanceMeters,
                        movingElapsedMillis = s.movingElapsedMillis,
                        publishedCount = s.publishedCount,
                        updatedAtMillis = System.currentTimeMillis(),
                        message = s.lastError,
                    ),
                )
            }
    }
}
