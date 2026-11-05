package com.gpswalk.diagnostic

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticActivity : ComponentActivity() {
    private val vm: DiagnosticViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0E1116), surface = Color(0xFF151A21))) {
                InspectorScreen(vm)
            }
        }
    }
}

private val Ok = Color(0xFF4CAF50)
private val MockColor = Color(0xFFFFB300)
private val Bad = Color(0xFFEF5350)
private val Neutral = Color(0xFF90A4AE)

fun statusColor(s: InspectorStatus) = when (s) {
    InspectorStatus.MOCK -> MockColor
    InspectorStatus.REAL -> Ok
    InspectorStatus.NO_LOCATION -> Neutral
    InspectorStatus.PERMISSION_DENIED, InspectorStatus.SERVICE_UNAVAILABLE -> Bad
}

@Composable
fun InspectorScreen(vm: DiagnosticViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.startListening() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.refresh()
        if (vm.state.value.permissionGranted) vm.startListening()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopListening() }

    InspectorContent(
        state = state,
        onRequestPermission = {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        onRestart = { vm.stopListening(); vm.startListening() },
        onClear = vm::clear,
    )
}

@Composable
fun InspectorContent(state: InspectorState, onRequestPermission: () -> Unit, onRestart: () -> Unit, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("GPS Location Inspector", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Independent receiver: reports exactly what Android delivers, including its mock flag.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val status = state.status
        val c = statusColor(status)
        Box(
            Modifier.fillMaxWidth().background(c.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).padding(20.dp)
                .testTag("inspector_status"),
            contentAlignment = Alignment.Center,
        ) {
            Text(status.label, color = c, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
        }
        if (status == InspectorStatus.PERMISSION_DENIED) {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Grant location permission") }
        }

        val latest = state.latest
        Section("Latest location") {
            Row2("Since last update", latest?.let { "%.1f s".format((state.nowMillis - it.receivedAtMillis).coerceAtLeast(0) / 1000.0) } ?: "—")
            Row2("Source", latest?.source ?: "—")
            Row2("Provider", latest?.provider ?: "—")
            Row2("Latitude", latest?.let { "%.6f".format(it.latitude) } ?: "—", mono = true)
            Row2("Longitude", latest?.let { "%.6f".format(it.longitude) } ?: "—", mono = true)
            Row2("Timestamp", latest?.let { fmt(it.timeMillis) } ?: "—")
            Row2("Accuracy", latest?.accuracy?.let { "±%.1f m".format(it) } ?: "not reported")
            Row2("Speed", latest?.speed?.let { "%.2f m/s (%.1f km/h)".format(it, it * 3.6f) } ?: "not reported")
            Row2("Bearing", latest?.bearing?.let { "%.1f°".format(it) } ?: "not reported")
            Row2("Altitude", latest?.altitude?.let { "%.1f m".format(it) } ?: "not reported")
            Row2("Android mock flag", latest?.let { if (it.isMock) "TRUE (mock)" else "false (real)" } ?: "—",
                color = latest?.let { if (it.isMock) MockColor else Ok })
        }
        Section("Per source") {
            listOf(LocationReceiver.SOURCE_FUSED, LocationReceiver.SOURCE_GPS).forEach { src ->
                val r = state.bySource[src]
                val n = state.countBySource[src] ?: 0
                Row2(src, if (r == null) "none" else "%d rx • %s • %.1f s ago".format(n, if (r.isMock) "MOCK" else "REAL",
                    (state.nowMillis - r.receivedAtMillis).coerceAtLeast(0) / 1000.0))
            }
            Row2("Mock / real received", "${state.mockCount} / ${state.realCount}")
        }
        Section("Environment") {
            Row2("Location permission", if (state.permissionGranted) "Granted" else "Denied", color = if (state.permissionGranted) Ok else Bad)
            Row2("Location services", if (state.locationEnabled) "On" else "Off", color = if (state.locationEnabled) Ok else Bad)
            Row2("Google Play services", if (state.playServicesAvailable) "Available" else "Unavailable")
            Row2("Listening", if (state.listening) "Yes" else "No")
            state.errors.forEach { Text(it, color = Bad, style = MaterialTheme.typography.bodySmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("Restart listening") }
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
        }
    }
}

private fun fmt(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFFAED581), fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Row2(label: String, value: String, mono: Boolean = false, color: Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        Text(value, color = color ?: MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null, fontWeight = FontWeight.Medium)
    }
}
