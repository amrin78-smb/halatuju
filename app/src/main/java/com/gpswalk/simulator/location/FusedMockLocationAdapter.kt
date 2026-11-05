package com.gpswalk.simulator.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.gpswalk.core.engine.LocationPublisher
import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.simulator.util.AppLog
import kotlinx.coroutines.tasks.await

/**
 * Injects mock locations into Google Play services' FusedLocationProviderClient using its
 * documented test API: setMockMode(true) + setMockLocation(). While mock mode is on, FLP
 * returns only the injected locations to its clients, flagged as mock.
 *
 * Requires: Play services, a location permission, and selection as the mock location app.
 */
class FusedMockLocationAdapter(context: Context) : LocationPublisher {
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private var mockModeEnabled = false

    override val name: String = "Play services FusedLocationProvider mock mode"

    override fun isAvailable(): Boolean =
        MockLocationStatus.playServicesAvailable(appContext) && hasLocationPermission()

    @SuppressLint("MissingPermission")
    override suspend fun initialize(): PublishResult {
        if (!MockLocationStatus.playServicesAvailable(appContext)) {
            return PublishResult.Failure("Google Play services unavailable", fatal = false)
        }
        if (!hasLocationPermission()) {
            return PublishResult.Failure("Location permission not granted (required by FLP mock mode)", fatal = false)
        }
        return try {
            client.setMockMode(true).await()
            mockModeEnabled = true
            AppLog.i("FLP mock mode enabled")
            PublishResult.Success("fused")
        } catch (e: SecurityException) {
            AppLog.e("FLP setMockMode SecurityException", e)
            PublishResult.Failure(AndroidMockLocationAdapter.NOT_SELECTED_MESSAGE, fatal = true, cause = e)
        } catch (e: Exception) {
            AppLog.e("FLP setMockMode failed", e)
            PublishResult.Failure("FLP mock mode failed: ${e.message}", fatal = false, cause = e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun publish(fix: SimulatedFix): PublishResult {
        if (!mockModeEnabled) return PublishResult.Failure("FLP mock mode not enabled", fatal = true)
        return try {
            client.setMockLocation(LocationFactory.create("fused", fix)).await()
            PublishResult.Success("fused")
        } catch (e: SecurityException) {
            AppLog.e("FLP setMockLocation SecurityException", e)
            PublishResult.Failure(AndroidMockLocationAdapter.NOT_SELECTED_MESSAGE, fatal = true, cause = e)
        } catch (e: Exception) {
            AppLog.e("FLP setMockLocation failed", e)
            PublishResult.Failure("FLP setMockLocation failed: ${e.message}", fatal = false, cause = e)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {
        if (!mockModeEnabled) return
        mockModeEnabled = false
        try {
            client.setMockMode(false).await()
            AppLog.i("FLP mock mode disabled")
        } catch (e: Exception) {
            AppLog.w("Failed to disable FLP mock mode", e)
        }
    }

    /** Turns FLP mock mode off unconditionally (stale-state cleanup after a process kill). */
    @SuppressLint("MissingPermission")
    suspend fun forceDisableMockMode() {
        if (!MockLocationStatus.playServicesAvailable(appContext) || !hasLocationPermission()) return
        try {
            client.setMockMode(false).await()
            AppLog.i("FLP mock mode force-disabled (stale cleanup)")
        } catch (e: Exception) {
            AppLog.w("Could not force-disable FLP mock mode", e)
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
