package com.gpswalk.diagnostic

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class InspectorState(
    val permissionGranted: Boolean = false,
    val locationEnabled: Boolean = false,
    val playServicesAvailable: Boolean = false,
    val listening: Boolean = false,
    val latest: ReceivedLocation? = null,
    val bySource: Map<String, ReceivedLocation> = emptyMap(),
    val countBySource: Map<String, Long> = emptyMap(),
    val mockCount: Long = 0,
    val realCount: Long = 0,
    val errors: List<String> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val status: InspectorStatus get() = InspectorStatus.resolve(permissionGranted, locationEnabled, latest)
}

class DiagnosticViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(InspectorState())
    val state: StateFlow<InspectorState> = _state.asStateFlow()

    private val receiver = LocationReceiver(app, ::onLocation, ::onError)

    init {
        // Ticker for "time since last location".
        viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(nowMillis = System.currentTimeMillis()) }
                delay(1_000)
            }
        }
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val enabled = LocationManagerCompat.isLocationEnabled(ctx.getSystemService(LocationManager::class.java))
        _state.update { it.copy(permissionGranted = granted, locationEnabled = enabled, playServicesAvailable = receiver.playServicesAvailable) }
    }

    /** Called when the screen becomes visible. */
    fun startListening() {
        refresh()
        if (!_state.value.permissionGranted) return
        _state.update { it.copy(errors = emptyList()) }
        receiver.start()
        _state.update { it.copy(listening = true) }
    }

    fun stopListening() {
        receiver.stop()
        _state.update { it.copy(listening = false) }
    }

    fun clear() = _state.update {
        it.copy(latest = null, bySource = emptyMap(), countBySource = emptyMap(), mockCount = 0, realCount = 0, errors = emptyList())
    }

    private fun onLocation(r: ReceivedLocation) = _state.update {
        it.copy(
            nowMillis = r.receivedAtMillis,
            latest = r,
            bySource = it.bySource + (r.source to r),
            countBySource = it.countBySource + (r.source to ((it.countBySource[r.source] ?: 0) + 1)),
            mockCount = it.mockCount + if (r.isMock) 1 else 0,
            realCount = it.realCount + if (r.isMock) 0 else 1,
        )
    }

    private fun onError(message: String) = _state.update { it.copy(errors = (it.errors + message).takeLast(5)) }

    override fun onCleared() {
        receiver.stop()
    }
}
