package com.gpswalk.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gpswalk.core.engine.SimulationSnapshot
import com.gpswalk.core.model.MovementProfile
import com.gpswalk.core.model.SimulationState
import com.gpswalk.core.model.UpdateInterval
import com.gpswalk.simulator.data.UserSettings
import com.gpswalk.simulator.ui.Diagnostics
import com.gpswalk.simulator.ui.RouteDraft
import com.gpswalk.simulator.ui.components.InfoRow
import com.gpswalk.simulator.ui.components.SectionCard
import com.gpswalk.simulator.ui.components.StatusRow
import com.gpswalk.simulator.ui.theme.StatusColors
import com.gpswalk.simulator.util.Format
import kotlin.math.roundToInt

fun stateColor(state: SimulationState) = when (state) {
    SimulationState.RUNNING -> StatusColors.Active
    SimulationState.PAUSED -> StatusColors.Warn
    SimulationState.COMPLETED -> StatusColors.Ok
    SimulationState.ERROR -> StatusColors.Error
    else -> StatusColors.Neutral
}

@Composable
fun SimulatorScreen(
    snapshot: SimulationSnapshot,
    settings: UserSettings,
    draft: RouteDraft,
    diagnostics: Diagnostics,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSpeed: (Double) -> Unit,
    onInterval: (UpdateInterval) -> Unit,
) {
    val state = snapshot.state
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlSection(state, onStart, onPause, onResume, onStop)
        LocationSection(snapshot)
        RouteSection(snapshot, draft)
        SpeedSection(settings, state, onSpeed, onInterval)
        StatisticsSection(snapshot)
        DiagnosticSection(snapshot, diagnostics)
    }
}

@Composable
private fun ControlSection(
    state: SimulationState,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onStop: () -> Unit,
) {
    SectionCard("Controls") {
        val canStart = !state.isActive
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStart, enabled = canStart,
                modifier = Modifier.weight(1f).height(56.dp).testTag("btn_start"),
            ) { Text("START") }
            FilledTonalButton(
                onClick = onPause, enabled = state == SimulationState.RUNNING,
                modifier = Modifier.weight(1f).height(56.dp).testTag("btn_pause"),
            ) { Text("PAUSE") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onResume, enabled = state == SimulationState.PAUSED,
                modifier = Modifier.weight(1f).height(56.dp).testTag("btn_resume"),
            ) { Text("RESUME") }
            Button(
                onClick = onStop, enabled = state.isActive,
                colors = ButtonDefaults.buttonColors(containerColor = StatusColors.Error),
                modifier = Modifier.weight(1f).height(56.dp).testTag("btn_stop"),
            ) { Text("STOP") }
        }
        if (state.isTerminal) {
            Text(
                "START will restart the selected route from the beginning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationSection(s: SimulationSnapshot) {
    SectionCard("Simulated location") {
        val f = s.currentFix
        InfoRow("Latitude", f?.let { Format.coord(it.latitude) } ?: "—", monospace = true)
        InfoRow("Longitude", f?.let { Format.coord(it.longitude) } ?: "—", monospace = true)
        InfoRow("Altitude", f?.let { "%.1f m".format(it.altitudeMeters) } ?: "—")
        InfoRow("Accuracy", f?.let { "±%.1f m".format(it.accuracyMeters) } ?: "—")
        InfoRow("Bearing", f?.let { "%.1f°".format(it.bearingDegrees) } ?: "—")
        InfoRow("Speed", f?.let { "%.2f m/s (%.1f km/h)".format(it.speedMetersPerSecond, MovementProfile.mpsToKmh(it.speedMetersPerSecond.toDouble())) } ?: "—")
    }
}

@Composable
private fun RouteSection(s: SimulationSnapshot, draft: RouteDraft) {
    SectionCard("Route") {
        val route = s.route
        if (route != null) {
            InfoRow("Name", route.name)
            InfoRow("Start", route.start.toString(), monospace = true)
            InfoRow("Destination", route.destination.toString(), monospace = true)
            InfoRow("Waypoints", "${route.waypoints.size}")
            InfoRow("Total distance", Format.distance(s.totalDistanceMeters))
            InfoRow("Progress", "%.1f %%".format(s.progressFraction * 100))
            LinearProgressIndicator(
                progress = { s.progressFraction.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            s.warnings.forEach { Text(it, color = StatusColors.Warn, style = MaterialTheme.typography.bodySmall) }
        } else {
            InfoRow("Selected", draft.name)
            InfoRow("Start", "${draft.startLat}, ${draft.startLon}", monospace = true)
            InfoRow("Destination", "${draft.destLat}, ${draft.destLon}", monospace = true)
            Text("Edit or choose routes on the Route tab.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSection(
    settings: UserSettings, state: SimulationState,
    onSpeed: (Double) -> Unit, onInterval: (UpdateInterval) -> Unit,
) {
    SectionCard("Walking speed") {
        Text(Format.speed(settings.speedKmh) + " – " + settings.profile.label, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementProfile.PRESETS.forEach { p ->
                FilterChip(
                    selected = settings.speedKmh == p.speedKmh,
                    onClick = { onSpeed(p.speedKmh) },
                    label = { Text("${p.label} ${p.speedKmh.roundToInt()}") },
                )
            }
        }
        var slider by remember(settings.speedKmh) { mutableFloatStateOf(settings.speedKmh.toFloat()) }
        Text("Custom: %.1f km/h".format(slider), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = slider,
            onValueChange = { slider = (it * 2).roundToInt() / 2f },
            onValueChangeFinished = { onSpeed(slider.toDouble()) },
            valueRange = MovementProfile.MIN_KMH.toFloat()..MovementProfile.MAX_KMH.toFloat(),
            steps = 13,
            modifier = Modifier.testTag("speed_slider"),
        )
        Text("Speed changes apply immediately, including during a simulation.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Update interval", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateInterval.entries.forEach { i ->
                FilterChip(
                    selected = settings.interval == i,
                    onClick = { onInterval(i) },
                    enabled = !state.isActive,
                    label = { Text(i.label) },
                )
            }
        }
    }
}

@Composable
private fun StatisticsSection(s: SimulationSnapshot) {
    SectionCard("Statistics") {
        InfoRow("Distance travelled", Format.distance(s.distanceTravelledMeters))
        InfoRow("Remaining", Format.distance(s.remainingMeters))
        InfoRow("Elapsed (moving) time", Format.duration(s.movingElapsedMillis))
        InfoRow("Current speed", s.currentFix?.let { Format.speed(MovementProfile.mpsToKmh(it.speedMetersPerSecond.toDouble())) } ?: "—")
        InfoRow("Locations published", s.publishedCount.toString())
        InfoRow("Last successful publish", s.lastPublishWallMillis?.let(Format::time) ?: "—")
    }
}

@Composable
private fun DiagnosticSection(s: SimulationSnapshot, d: Diagnostics) {
    SectionCard("Diagnostics") {
        StatusRow(
            "Selected as mock location app",
            if (d.mockAppSelected) "Yes" else "No",
            if (d.mockAppSelected) StatusColors.Ok else StatusColors.Error,
        )
        StatusRow(
            "Developer options",
            when (d.developerOptionsEnabled) { true -> "Enabled"; false -> "Disabled"; null -> "Unknown" },
            when (d.developerOptionsEnabled) { true -> StatusColors.Ok; false -> StatusColors.Error; null -> StatusColors.Neutral },
        )
        StatusRow("Location services", if (d.locationEnabled) "On" else "Off", if (d.locationEnabled) StatusColors.Ok else StatusColors.Warn)
        StatusRow("Location permission", if (d.locationPermission) "Granted" else "Not granted",
            if (d.locationPermission) StatusColors.Ok else StatusColors.Warn)
        StatusRow("Notification permission", if (d.notificationPermission) "Granted" else "Not granted",
            if (d.notificationPermission) StatusColors.Ok else StatusColors.Warn)
        StatusRow("Google Play services", if (d.playServicesAvailable) "Available" else "Unavailable",
            if (d.playServicesAvailable) StatusColors.Ok else StatusColors.Warn)
        val providerActive = s.state.isActive && s.lastInjectionOk == true
        StatusRow(
            "Mock provider",
            when {
                providerActive -> "Injecting"
                s.state.isActive && s.lastInjectionOk == false -> "Failing"
                s.state.isActive -> "Starting…"
                else -> "Inactive"
            },
            when {
                providerActive -> StatusColors.Ok
                s.state.isActive && s.lastInjectionOk == false -> StatusColors.Error
                else -> StatusColors.Neutral
            },
        )
        StatusRow(
            "Last injection",
            s.lastInjectionResult,
            when (s.lastInjectionOk) { true -> StatusColors.Ok; false -> StatusColors.Error; null -> StatusColors.Neutral },
        )
        InfoRow("Channels", s.publisherName)
        InfoRow("Most recent error", s.lastError ?: "None", valueColor = if (s.lastError != null) StatusColors.Error else StatusColors.Neutral)
        InfoRow("Android SDK", "${d.sdkInt} (${d.device})")
    }
}
