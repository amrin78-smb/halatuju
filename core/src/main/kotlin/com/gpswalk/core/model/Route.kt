package com.gpswalk.core.model

/**
 * An ordered list of waypoints. The first waypoint is the start, the last is the destination.
 */
data class Route(
    val name: String,
    val waypoints: List<Coordinate>,
) {
    val start: Coordinate? get() = waypoints.firstOrNull()
    val destination: Coordinate? get() = waypoints.lastOrNull()

    /** Removes consecutive duplicate waypoints (zero-length segments). */
    fun withoutConsecutiveDuplicates(): Route {
        val cleaned = mutableListOf<Coordinate>()
        for (point in waypoints) {
            if (cleaned.isEmpty() || !cleaned.last().isSamePlaceAs(point)) cleaned += point
        }
        return copy(waypoints = cleaned)
    }

    /**
     * Validates the route for simulation. Consecutive duplicate waypoints are reported as a
     * warning and removed; a route needs at least two distinct points.
     */
    fun validate(): RouteValidation {
        if (name.isBlank()) return RouteValidation.Invalid("Route name is empty")
        if (waypoints.isEmpty()) return RouteValidation.Invalid("Route has no waypoints")
        if (waypoints.size < 2) return RouteValidation.Invalid("Route needs a start and a destination")
        val cleaned = withoutConsecutiveDuplicates()
        if (cleaned.waypoints.size < 2) {
            return RouteValidation.Invalid("All waypoints are at the same location; the route has zero length")
        }
        val removed = waypoints.size - cleaned.waypoints.size
        val warnings = if (removed > 0) listOf("Removed $removed duplicate consecutive waypoint(s)") else emptyList()
        return RouteValidation.Valid(cleaned, warnings)
    }

    companion object {
        /** Built-in test routes (test coordinates only; not claims about safe pedestrian paths). */
        val PRESET_SHORT = Route(
            "Route A – Short Test",
            listOf(Coordinate(13.7563, 100.5018), Coordinate(13.7573, 100.5018)),
        )
        val PRESET_MEDIUM = Route(
            "Route B – Medium Test",
            listOf(Coordinate(13.7563, 100.5018), Coordinate(13.7563, 100.5118)),
        )
        val PRESET_MULTI = Route(
            "Route C – Multi-Waypoint Test",
            listOf(
                Coordinate(13.7563, 100.5018),
                Coordinate(13.7573, 100.5018),
                Coordinate(13.7573, 100.5038),
                Coordinate(13.7563, 100.5038),
            ),
        )
        val PRESETS = listOf(PRESET_SHORT, PRESET_MEDIUM, PRESET_MULTI)
    }
}

sealed interface RouteValidation {
    data class Valid(val route: Route, val warnings: List<String>) : RouteValidation
    data class Invalid(val reason: String) : RouteValidation
}
