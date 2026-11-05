package com.gpswalk.simulator.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpswalk.core.engine.SimulationSnapshot
import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.Route
import com.gpswalk.core.model.UpdateInterval
import com.gpswalk.simulator.data.UserSettings
import com.gpswalk.simulator.gpsWalkApp
import com.gpswalk.simulator.location.MockLocationStatus
import com.gpswalk.simulator.permissions.Permissions
import com.gpswalk.simulator.service.WalkingSimulationService
import com.gpswalk.simulator.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable text form of a route; parsed and validated only when used. */
data class RouteDraft(
    val name: String,
    val startLat: String,
    val startLon: String,
    val waypoints: List<Pair<String, String>>,
    val destLat: String,
    val destLon: String,
) {
    fun toRoute(): Result<Route> = runCatching {
        fun parse(label: String, lat: String, lon: String) =
            Coordinate.parse(lat, lon).getOrElse { throw IllegalArgumentException("$label: ${it.message}") }
        val points = buildList {
            add(parse("Start", startLat, startLon))
            waypoints.forEachIndexed { i, (lat, lon) -> add(parse("Waypoint ${i + 1}", lat, lon)) }
            add(parse("Destination", destLat, destLon))
        }
        Route(name.trim(), points)
    }

    companion object {
        fun from(route: Route): RouteDraft {
            val pts = route.waypoints
            fun s(v: Double) = v.toBigDecimal().stripTrailingZeros().toPlainString()
            return RouteDraft(
                name = route.name,
                startLat = s(pts.first().latitude), startLon = s(pts.first().longitude),
                waypoints = pts.drop(1).dropLast(1).map { s(it.latitude) to s(it.longitude) },
                destLat = s(pts.last().latitude), destLon = s(pts.last().longitude),
            )
        }
    }
}

data class Diagnostics(
    val mockAppSelected: Boolean = false,
    val developerOptionsEnabled: Boolean? = null,
    val locationEnabled: Boolean = false,
    val playServicesAvailable: Boolean = false,
    val locationPermission: Boolean = false,
    val notificationPermission: Boolean = false,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val device: String = "${Build.MANUFACTURER} ${Build.MODEL}",
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = app.gpsWalkApp.controller
    private val settingsRepo = app.gpsWalkApp.settingsRepository
    private val routeRepo = app.gpsWalkApp.routeRepository

    val snapshot: StateFlow<SimulationSnapshot> = controller.snapshot
    val notice: StateFlow<String?> = controller.notice
    val settings: StateFlow<UserSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val savedRoutes: StateFlow<List<Route>> = routeRepo.savedRoutes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _draft = MutableStateFlow(RouteDraft.from(Route.PRESET_SHORT))
    val draft: StateFlow<RouteDraft> = _draft.asStateFlow()

    private val _diagnostics = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diagnostics.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** Transient user-facing message (validation errors, confirmations). */
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshDiagnostics()
        viewModelScope.launch {
            // Restore the last selected route (preset or saved) for convenience.
            val name = settingsRepo.current().lastRouteName ?: return@launch
            val route = (Route.PRESETS + routeRepo.savedRoutes.first()).firstOrNull { it.name == name }
            if (route != null && !snapshot.value.state.isActive) _draft.value = RouteDraft.from(route)
        }
    }

    fun refreshDiagnostics() {
        val ctx = getApplication<Application>()
        _diagnostics.value = Diagnostics(
            mockAppSelected = MockLocationStatus.isSelectedMockLocationApp(ctx),
            developerOptionsEnabled = MockLocationStatus.developerOptionsEnabled(ctx),
            locationEnabled = MockLocationStatus.locationServicesEnabled(ctx),
            playServicesAvailable = MockLocationStatus.playServicesAvailable(ctx),
            locationPermission = Permissions.hasLocation(ctx),
            notificationPermission = Permissions.hasNotifications(ctx),
        )
    }

    fun updateDraft(transform: (RouteDraft) -> RouteDraft) = _draft.update(transform)

    fun selectRoute(route: Route) {
        _draft.value = RouteDraft.from(route)
        viewModelScope.launch { settingsRepo.setLastRouteName(route.name) }
    }

    fun saveDraft() {
        val route = _draft.value.toRoute().getOrElse { _message.value = it.message; return }
        if (Route.PRESETS.any { it.name == route.name }) {
            _message.value = "Choose a different name: presets cannot be overwritten"
            return
        }
        when (val v = route.validate()) {
            is com.gpswalk.core.model.RouteValidation.Invalid -> _message.value = v.reason
            is com.gpswalk.core.model.RouteValidation.Valid -> viewModelScope.launch {
                routeRepo.save(v.route)
                settingsRepo.setLastRouteName(v.route.name)
                _message.value = "Saved “${v.route.name}”"
            }
        }
    }

    fun deleteSavedRoute(name: String) = viewModelScope.launch { routeRepo.delete(name) }

    fun deleteAllData() = viewModelScope.launch {
        routeRepo.deleteAll()
        settingsRepo.clearRecords()
        controller.dismissNotice()
        _message.value = "Saved routes and simulation records deleted"
    }

    fun setSpeed(kmh: Double) {
        val profile = runCatching { MovementProfile.PRESETS.firstOrNull { it.speedKmh == kmh } ?: MovementProfile.custom(kmh) }
            .getOrElse { _message.value = it.message; return }
        controller.setSpeed(profile)
        viewModelScope.launch { settingsRepo.setSpeed(profile.speedKmh) }
    }

    fun setInterval(interval: UpdateInterval) = viewModelScope.launch { settingsRepo.setInterval(interval) }
    fun setUseFused(enabled: Boolean) = viewModelScope.launch { settingsRepo.setUseFused(enabled) }

    /** Loads the draft route and starts the foreground service. Call after permissions are handled. */
    fun start() {
        val route = _draft.value.toRoute().getOrElse { _message.value = it.message; return }
        val s = settings.value
        val error = controller.load(route, s.profile, s.interval, s.useFusedChannel)
        if (error != null) { _message.value = error; return }
        viewModelScope.launch { settingsRepo.setLastRouteName(route.name) }
        try {
            WalkingSimulationService.start(getApplication())
        } catch (e: Exception) {
            AppLog.e("startForegroundService failed", e)
            controller.reportFailure("Could not start the foreground service: ${e.message}")
        }
    }

    fun pause() { controller.pause() }
    fun resume() { controller.resume() }
    fun stop() { controller.stop() }

    fun dismissMessage() { _message.value = null }
    fun dismissNotice() = controller.dismissNotice()
    fun showMessage(text: String) { _message.value = text }
}
