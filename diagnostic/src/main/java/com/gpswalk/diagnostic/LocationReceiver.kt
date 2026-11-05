package com.gpswalk.diagnostic

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Independently subscribes to Android location updates through two documented paths:
 *  - Google Play services FusedLocationProviderClient (preferred, when available), and
 *  - framework LocationManager GPS_PROVIDER (fallback / cross-check).
 * Nothing here talks to the simulator.
 */
class LocationReceiver(context: Context, private val onLocation: (ReceivedLocation) -> Unit, private val onError: (String) -> Unit) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val fused = LocationServices.getFusedLocationProviderClient(appContext)

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { deliver(SOURCE_FUSED, ReceivedLocation.from(SOURCE_FUSED, it)) }
        }
    }
    private val gpsListener = LocationListener { deliver(SOURCE_GPS, ReceivedLocation.from(SOURCE_GPS, it)) }

    var fusedActive = false; private set
    var gpsActive = false; private set

    val playServicesAvailable: Boolean
        get() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    @SuppressLint("MissingPermission") // Caller checks permission before start().
    fun start() {
        stop()
        if (playServicesAvailable) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .build()
            fused.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnSuccessListener { fusedActive = true; Log.i(TAG, "Fused updates requested") }
                .addOnFailureListener { e -> onError("Fused provider: ${e.message}"); Log.e(TAG, "Fused request failed", e) }
        } else {
            onError("Google Play services unavailable; using LocationManager only")
        }
        try {
            if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, gpsListener, Looper.getMainLooper())
                gpsActive = true
                Log.i(TAG, "LocationManager GPS updates requested")
            } else {
                onError("GPS provider not present on this device")
            }
        } catch (e: SecurityException) {
            onError("LocationManager: permission denied")
            Log.e(TAG, "GPS request failed", e)
        } catch (e: IllegalArgumentException) {
            onError("LocationManager GPS: ${e.message}")
            Log.e(TAG, "GPS request failed", e)
        }
    }

    fun stop() {
        if (fusedActive || playServicesAvailable) fused.removeLocationUpdates(fusedCallback)
        locationManager.removeUpdates(gpsListener)
        fusedActive = false
        gpsActive = false
    }

    private fun deliver(source: String, r: ReceivedLocation) {
        // Logged so the delivery can be verified over adb without reading the screen:
        //   adb logcat -s GPSLocationInspector
        Log.i(
            TAG,
            "RX source=$source provider=${r.provider} lat=%.6f lon=%.6f acc=%s spd=%s brg=%s mock=%b time=%d"
                .format(r.latitude, r.longitude, r.accuracy, r.speed, r.bearing, r.isMock, r.timeMillis),
        )
        onLocation(r)
    }

    companion object {
        const val TAG = "GPSLocationInspector"
        const val SOURCE_FUSED = "Fused (Play services)"
        const val SOURCE_GPS = "LocationManager GPS"
    }
}
