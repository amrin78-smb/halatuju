package com.gpswalk.simulator

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpswalk.simulator.permissions.Permissions
import com.gpswalk.simulator.ui.MainViewModel
import com.gpswalk.simulator.ui.components.StatusPill
import com.gpswalk.simulator.ui.screens.RouteScreen
import com.gpswalk.simulator.ui.screens.SetupScreen
import com.gpswalk.simulator.ui.screens.SimulatorScreen
import com.gpswalk.simulator.ui.screens.stateColor
import com.gpswalk.simulator.ui.theme.GpsWalkTheme
import com.gpswalk.simulator.ui.theme.StatusColors

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GpsWalkTheme { MainScreen(viewModel) } }
    }
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val diagnostics by vm.diagnostics.collectAsStateWithLifecycle()
    val savedRoutes by vm.savedRoutes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDiagnostics() }
    LaunchedEffect(snapshot.state) { vm.refreshDiagnostics() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.dismissMessage() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.refreshDiagnostics()
        val location = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!location) {
            vm.showMessage("Location permission denied. It is required to run a location foreground service.")
            return@rememberLauncherForActivityResult
        }
        if (result[Manifest.permission.POST_NOTIFICATIONS] == false) {
            vm.showMessage("Notifications denied: the simulation will run, but its notification controls are hidden.")
        }
        vm.start()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(snapshot.state.name, stateColor(snapshot.state))
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Simulator", "Route", "Setup").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }, modifier = Modifier.testTag("tab_$title"))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                notice?.let { Banner(it, onDismiss = vm::dismissNotice) }
                if (!diagnostics.mockAppSelected && tab != 2) {
                    Banner("Not selected as mock location app. See the Setup tab.", onDismiss = null, action = "Setup") { tab = 2 }
                }
                when (tab) {
                    0 -> SimulatorScreen(
                        snapshot = snapshot, settings = settings, draft = draft, diagnostics = diagnostics,
                        onStart = {
                            val ctx = vm.getApplication<GpsWalkApplication>()
                            if (Permissions.hasLocation(ctx) && Permissions.hasNotifications(ctx)) vm.start()
                            else permissionLauncher.launch(Permissions.startRequirements())
                        },
                        onPause = vm::pause, onResume = vm::resume, onStop = vm::stop,
                        onSpeed = vm::setSpeed, onInterval = { vm.setInterval(it) },
                    )
                    1 -> RouteScreen(
                        draft = draft, savedRoutes = savedRoutes, editable = !snapshot.state.isActive,
                        onSelect = vm::selectRoute, onUpdate = vm::updateDraft, onSave = vm::saveDraft,
                        onDeleteSaved = { vm.deleteSavedRoute(it) },
                    )
                    else -> SetupScreen(
                        settings = settings, diagnostics = diagnostics, simulationActive = snapshot.state.isActive,
                        onUseFused = { vm.setUseFused(it) }, onDeleteAll = { vm.deleteAllData() }, onMessage = vm::showMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("GPS Walk Simulator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("v${BuildConfig.VERSION_NAME} • mock location test utility", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(state, color, Modifier.testTag("status_pill"))
    }
}

@Composable
private fun Banner(text: String, onDismiss: (() -> Unit)?, action: String? = null, onAction: () -> Unit = {}) {
    Card(colors = CardDefaults.cardColors(containerColor = StatusColors.Warn.copy(alpha = 0.15f))) {
        Column(Modifier.padding(12.dp)) {
            Text(text, color = StatusColors.Warn, style = MaterialTheme.typography.bodyMedium)
            Row {
                if (action != null) TextButton(onClick = onAction) { Text(action) }
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
