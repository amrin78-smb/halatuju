package com.gpswalk.simulator.location

import android.location.Location
import android.os.Build
import com.gpswalk.core.model.SimulatedFix

/** Converts an engine fix into a complete android.location.Location. */
internal object LocationFactory {
    fun create(provider: String, fix: SimulatedFix): Location = Location(provider).apply {
        latitude = fix.latitude
        longitude = fix.longitude
        altitude = fix.altitudeMeters
        accuracy = fix.accuracyMeters
        bearing = fix.bearingDegrees
        speed = fix.speedMetersPerSecond
        // Both timestamps are mandatory: the framework rejects "incomplete" locations.
        time = fix.timeMillis
        elapsedRealtimeNanos = fix.elapsedRealtimeNanos
        verticalAccuracyMeters = fix.accuracyMeters * 1.5f
        speedAccuracyMetersPerSecond = 0.5f
        bearingAccuracyDegrees = 10f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Explicitly mark as mock. The platform also forces this flag for test-provider
            // locations; we never attempt to clear it.
            isMock = true
        }
    }
}
