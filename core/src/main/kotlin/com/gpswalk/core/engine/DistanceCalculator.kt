package com.gpswalk.core.engine

import com.gpswalk.core.model.Coordinate
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spherical-earth geodesy. The spherical model (mean radius 6,371,008.8 m) is accurate to
 * ~0.3% versus the WGS84 ellipsoid, which is far below GPS noise at walking scale.
 */
object DistanceCalculator {
    const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance in metres using the Haversine formula. */
    fun haversineMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from [from] to [to], degrees clockwise from north in 0..360. */
    fun initialBearingDegrees(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    /**
     * Point at [fraction] (0..1) along the great circle from [a] to [b] (spherical linear
     * interpolation). Altitude is interpolated linearly.
     */
    fun intermediatePoint(a: Coordinate, b: Coordinate, fraction: Double): Coordinate {
        val f = fraction.coerceIn(0.0, 1.0)
        if (f == 0.0) return a
        if (f == 1.0) return b
        val lat1 = Math.toRadians(a.latitude)
        val lon1 = Math.toRadians(a.longitude)
        val lat2 = Math.toRadians(b.latitude)
        val lon2 = Math.toRadians(b.longitude)
        val delta = haversineMeters(a, b) / EARTH_RADIUS_METERS
        val altitude = a.altitudeMeters + (b.altitudeMeters - a.altitudeMeters) * f
        if (delta < 1e-12) return Coordinate(a.latitude, a.longitude, altitude)
        val sinDelta = sin(delta)
        val wa = sin((1 - f) * delta) / sinDelta
        val wb = sin(f * delta) / sinDelta
        val x = wa * cos(lat1) * cos(lon1) + wb * cos(lat2) * cos(lon2)
        val y = wa * cos(lat1) * sin(lon1) + wb * cos(lat2) * sin(lon2)
        val z = wa * sin(lat1) + wb * sin(lat2)
        val lat = atan2(z, sqrt(x * x + y * y))
        val lon = atan2(y, x)
        return Coordinate(Math.toDegrees(lat), normalizeLongitude(Math.toDegrees(lon)), altitude)
    }

    fun normalizeBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    private fun normalizeLongitude(degrees: Double): Double {
        var lon = degrees
        while (lon > 180.0) lon -= 360.0
        while (lon < -180.0) lon += 360.0
        return lon
    }
}
