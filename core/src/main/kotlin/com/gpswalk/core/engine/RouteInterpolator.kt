package com.gpswalk.core.engine

import com.gpswalk.core.model.Coordinate
import com.gpswalk.core.model.Route

/** Position on a route at a given travelled distance. */
data class RoutePosition(
    val coordinate: Coordinate,
    /** Bearing toward the next waypoint (or along the final segment once arrived). */
    val bearingDegrees: Double,
    val segmentIndex: Int,
    val distanceAlongRouteMeters: Double,
    val isAtEnd: Boolean,
)

/**
 * Maps travelled distance to a coordinate by walking great-circle segments between waypoints.
 * The route must already be validated (>= 2 points, no zero-length segments).
 */
class RouteInterpolator(val route: Route) {
    private val points = route.waypoints
    private val segmentLengths: DoubleArray
    /** cumulative[i] = distance from start to waypoint i. */
    private val cumulative: DoubleArray

    init {
        require(points.size >= 2) { "Route needs at least two waypoints" }
        segmentLengths = DoubleArray(points.size - 1) { DistanceCalculator.haversineMeters(points[it], points[it + 1]) }
        require(segmentLengths.all { it > 0.0 }) { "Route contains a zero-length segment (duplicate waypoints)" }
        cumulative = DoubleArray(points.size)
        for (i in segmentLengths.indices) cumulative[i + 1] = cumulative[i] + segmentLengths[i]
    }

    val totalDistanceMeters: Double get() = cumulative.last()

    fun positionAt(distanceMeters: Double): RoutePosition {
        val d = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        if (d >= totalDistanceMeters) {
            val last = segmentLengths.lastIndex
            return RoutePosition(
                coordinate = points.last(),
                // Final great-circle bearing of the last segment = reverse initial bearing + 180°.
                bearingDegrees = DistanceCalculator.normalizeBearing(
                    DistanceCalculator.initialBearingDegrees(points[last + 1], points[last]) + 180.0,
                ),
                segmentIndex = last,
                distanceAlongRouteMeters = totalDistanceMeters,
                isAtEnd = true,
            )
        }
        // Find the segment containing d (cumulative is sorted; few segments, linear scan is fine).
        var segment = 0
        while (segment < segmentLengths.lastIndex && d >= cumulative[segment + 1]) segment++
        val fraction = (d - cumulative[segment]) / segmentLengths[segment]
        val coordinate = DistanceCalculator.intermediatePoint(points[segment], points[segment + 1], fraction)
        val bearing = DistanceCalculator.initialBearingDegrees(
            if (fraction == 0.0) points[segment] else coordinate,
            points[segment + 1],
        )
        return RoutePosition(coordinate, bearing, segment, d, isAtEnd = false)
    }
}
