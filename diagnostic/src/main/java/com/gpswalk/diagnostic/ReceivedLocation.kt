package com.gpswalk.diagnostic

import android.location.Location
import android.os.Build

/** Snapshot of a location exactly as Android delivered it, including Android's mock flag. */
data class ReceivedLocation(
    val source: String,
    val provider: String?,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val timeMillis: Long,
    /** Reported by Android: Location.isMock (API 31+) or isFromMockProvider (API 18–30). */
    val isMock: Boolean,
    val receivedAtMillis: Long,
) {
    companion object {
        fun from(source: String, l: Location, receivedAt: Long = System.currentTimeMillis()) = ReceivedLocation(
            source = source,
            provider = l.provider,
            latitude = l.latitude,
            longitude = l.longitude,
            altitude = if (l.hasAltitude()) l.altitude else null,
            accuracy = if (l.hasAccuracy()) l.accuracy else null,
            speed = if (l.hasSpeed()) l.speed else null,
            bearing = if (l.hasBearing()) l.bearing else null,
            timeMillis = l.time,
            isMock = isMock(l),
            receivedAtMillis = receivedAt,
        )

        fun isMock(l: Location): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) l.isMock
            else @Suppress("DEPRECATION") l.isFromMockProvider
    }
}

enum class InspectorStatus(val label: String) {
    NO_LOCATION("NO LOCATION RECEIVED"),
    REAL("REAL LOCATION RECEIVED"),
    MOCK("MOCK LOCATION RECEIVED"),
    PERMISSION_DENIED("LOCATION PERMISSION DENIED"),
    SERVICE_UNAVAILABLE("LOCATION SERVICE UNAVAILABLE");

    companion object {
        /** Pure status decision, unit-tested. Mock status comes only from Android's flag. */
        fun resolve(permissionGranted: Boolean, locationEnabled: Boolean, latest: ReceivedLocation?): InspectorStatus = when {
            !permissionGranted -> PERMISSION_DENIED
            !locationEnabled -> SERVICE_UNAVAILABLE
            latest == null -> NO_LOCATION
            latest.isMock -> MOCK
            else -> REAL
        }
    }
}
