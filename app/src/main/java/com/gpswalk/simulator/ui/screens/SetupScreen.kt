package com.gpswalk.simulator.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.gpswalk.simulator.data.UserSettings
import com.gpswalk.simulator.ui.Diagnostics
import com.gpswalk.simulator.ui.components.SectionCard
import com.gpswalk.simulator.ui.components.StatusRow
import com.gpswalk.simulator.ui.theme.StatusColors

@Composable
fun SetupScreen(
    settings: UserSettings,
    diagnostics: Diagnostics,
    simulationActive: Boolean,
    onUseFused: (Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { onMessage("Settings screen not available on this device") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Mock location setup") {
            StatusRow("Selected as mock location app", if (diagnostics.mockAppSelected) "Yes" else "No",
                if (diagnostics.mockAppSelected) StatusColors.Ok else StatusColors.Error)
            Text(
                "1. Enable Developer options: Settings > About phone > Software information > tap Build number 7 times.\n" +
                    "2. Settings > Developer options > Select mock location app > GPS Walk Simulator.\n" +
                    "3. Keep Location turned on.\n\n" +
                    "This app never changes these settings itself. Locations it injects are always flagged " +
                    "by Android as mock locations.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()) { Text("Open Developer options") }
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()) { Text("Open Location settings") }
            OutlinedButton(
                onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("App info (permissions, battery)") }
        }
        SectionCard("Injection channels") {
            Text("Always: Android LocationManager test providers (gps + network).", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Also use Play services fused mock mode", style = MaterialTheme.typography.bodyLarge)
                    Text("Makes apps using FusedLocationProviderClient receive the simulated location reliably. Applies on next START.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.useFusedChannel, onCheckedChange = onUseFused, enabled = !simulationActive)
            }
        }
        SectionCard("Background execution on Samsung") {
            Text(
                "The simulation runs in a foreground service with a persistent notification. One UI battery " +
                    "management can still stop apps. For long runs: Settings > Apps > GPS Walk Simulator > Battery > " +
                    "Unrestricted, and do not add the app to Sleeping/Deep sleeping apps. If the app is stopped, it reports " +
                    "this the next time it opens and does not restart automatically.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("Privacy") {
            Text("All data stays on this device. Only settings, saved routes and the last run's totals are stored — " +
                "no location history. The app has no internet permission.", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(containerColor = StatusColors.Error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete saved routes and records") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete data?") },
            text = { Text("All saved routes and simulation records will be removed. Presets are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAll() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
