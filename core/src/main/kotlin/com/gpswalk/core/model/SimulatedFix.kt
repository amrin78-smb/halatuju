package com.gpswalk.core.model

/**
 * A platform-neutral simulated location sample. The Android layer converts this into
 * an android.location.Location before injection.
 */
data class SimulatedFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    /** Degrees clockwise from true north, 0..360. */
    val bearingDegrees: Float,
    val speedMetersPerSecond: Float,
    /** Wall-clock UTC time in milliseconds. */
    val timeMillis: Long,
    /** Monotonic time (Android: SystemClock.elapsedRealtimeNanos). */
    val elapsedRealtimeNanos: Long,
)
