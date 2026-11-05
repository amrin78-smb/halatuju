package com.gpswalk.core.model

/**
 * A WGS84 geographic coordinate. Construct via [Coordinate.of] to get validation,
 * or use the primary constructor when the values are already known to be valid.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
) {
    init {
        require(isValidLatitude(latitude)) { "Latitude $latitude is outside -90..90" }
        require(isValidLongitude(longitude)) { "Longitude $longitude is outside -180..180" }
        require(altitudeMeters.isFinite()) { "Altitude must be finite" }
    }

    /** True when both coordinates are within [toleranceDegrees] of each other. */
    fun isSamePlaceAs(other: Coordinate, toleranceDegrees: Double = 1e-9): Boolean =
        kotlin.math.abs(latitude - other.latitude) <= toleranceDegrees &&
            kotlin.math.abs(longitude - other.longitude) <= toleranceDegrees

    override fun toString(): String = "%.6f, %.6f".format(latitude, longitude)

    companion object {
        fun isValidLatitude(value: Double): Boolean = value.isFinite() && value in -90.0..90.0
        fun isValidLongitude(value: Double): Boolean = value.isFinite() && value in -180.0..180.0

        /** Parses and validates user input. Returns null-safe [Result]. */
        fun parse(latitude: String, longitude: String, altitude: String = ""): Result<Coordinate> {
            val lat = latitude.trim().toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Latitude '$latitude' is not a number"))
            val lon = longitude.trim().toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Longitude '$longitude' is not a number"))
            val alt = if (altitude.isBlank()) 0.0 else altitude.trim().toDoubleOrNull()
                ?: return Result.failure(IllegalArgumentException("Altitude '$altitude' is not a number"))
            return runCatching { Coordinate(lat, lon, alt) }
        }
    }
}
