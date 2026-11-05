package com.gpswalk.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gpswalk.core.model.Route
import com.gpswalk.simulator.ui.RouteDraft
import com.gpswalk.simulator.ui.components.SectionCard
import com.gpswalk.simulator.util.Format
import com.gpswalk.core.engine.DistanceCalculator

@Composable
fun RouteScreen(
    draft: RouteDraft,
    savedRoutes: List<Route>,
    editable: Boolean,
    onSelect: (Route) -> Unit,
    onUpdate: ((RouteDraft) -> RouteDraft) -> Unit,
    onSave: () -> Unit,
    onDeleteSaved: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!editable) {
            Text("A simulation is active. Stop it to change the route.", color = MaterialTheme.colorScheme.error)
        }
        SectionCard("Preset test routes") {
            Route.PRESETS.forEach { r -> RouteRow(r, editable, onSelect, null) }
            Text("Test coordinates only; not claims about safe pedestrian routes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Saved routes") {
            if (savedRoutes.isEmpty()) Text("No saved routes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            savedRoutes.forEach { r -> RouteRow(r, editable, onSelect, onDeleteSaved) }
        }
        SectionCard("Route editor") {
            OutlinedTextField(draft.name, { v -> onUpdate { it.copy(name = v) } }, label = { Text("Route name") },
                enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
            CoordinateFields("Start", draft.startLat, draft.startLon, editable,
                { v -> onUpdate { it.copy(startLat = v) } }, { v -> onUpdate { it.copy(startLon = v) } })
            draft.waypoints.forEachIndexed { i, (lat, lon) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Waypoint ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { onUpdate { d -> d.copy(waypoints = d.waypoints.filterIndexed { j, _ -> j != i }) } },
                        enabled = editable) { Text("Remove") }
                }
                CoordinateFields(null, lat, lon, editable,
                    { v -> onUpdate { d -> d.copy(waypoints = d.waypoints.mapIndexed { j, w -> if (j == i) v to w.second else w }) } },
                    { v -> onUpdate { d -> d.copy(waypoints = d.waypoints.mapIndexed { j, w -> if (j == i) w.first to v else w }) } })
            }
            OutlinedButton(
                onClick = { onUpdate { d -> d.copy(waypoints = d.waypoints + (d.destLat to d.destLon)) } },
                enabled = editable, modifier = Modifier.fillMaxWidth(),
            ) { Text("Add intermediate waypoint") }
            CoordinateFields("Destination", draft.destLat, draft.destLon, editable,
                { v -> onUpdate { it.copy(destLat = v) } }, { v -> onUpdate { it.copy(destLon = v) } })
            draft.toRoute().onSuccess { r ->
                val v = r.validate()
                Text(
                    when (v) {
                        is com.gpswalk.core.model.RouteValidation.Valid ->
                            "Valid route • ${Format.distance(r.withoutConsecutiveDuplicates().waypoints.zipWithNext { a, b -> DistanceCalculator.haversineMeters(a, b) }.sum())}" +
                                v.warnings.joinToString("") { " • $it" }
                        is com.gpswalk.core.model.RouteValidation.Invalid -> "Invalid: ${v.reason}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (v is com.gpswalk.core.model.RouteValidation.Valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }.onFailure { Text("Invalid: ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onSave, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("Save route") }
        }
    }
}

@Composable
private fun RouteRow(r: Route, editable: Boolean, onSelect: (Route) -> Unit, onDelete: ((String) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.bodyLarge)
            val dist = r.waypoints.zipWithNext { a, b -> DistanceCalculator.haversineMeters(a, b) }.sum()
            Text("${r.waypoints.size} points • ${Format.distance(dist)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistChip(onClick = { onSelect(r) }, label = { Text("Use") }, enabled = editable)
        if (onDelete != null) TextButton(onClick = { onDelete(r.name) }) { Text("Delete") }
    }
}

@Composable
private fun CoordinateFields(
    title: String?, lat: String, lon: String, enabled: Boolean,
    onLat: (String) -> Unit, onLon: (String) -> Unit,
) {
    if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
    val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(lat, onLat, label = { Text("Latitude") }, singleLine = true, enabled = enabled,
            keyboardOptions = kb, modifier = Modifier.weight(1f))
        OutlinedTextField(lon, onLon, label = { Text("Longitude") }, singleLine = true, enabled = enabled,
            keyboardOptions = kb, modifier = Modifier.weight(1f))
    }
}
