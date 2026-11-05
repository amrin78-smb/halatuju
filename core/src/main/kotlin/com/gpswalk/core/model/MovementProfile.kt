package com.gpswalk.core.model

/** Walking speed. Custom speeds are limited to [MIN_KMH]..[MAX_KMH]. */
data class MovementProfile(val label: String, val speedKmh: Double) {
    init {
        require(speedKmh.isFinite() && speedKmh in MIN_KMH..MAX_KMH) {
            "Speed must be between $MIN_KMH and $MAX_KMH km/h (was $speedKmh)"
        }
    }

    val speedMetersPerSecond: Double get() = kmhToMps(speedKmh)

    companion object {
        const val MIN_KMH = 1.0
        const val MAX_KMH = 8.0

        val SLOW = MovementProfile("Slow walk", 2.0)
        val NORMAL = MovementProfile("Normal walk", 4.0)
        val BRISK = MovementProfile("Brisk walk", 5.0)
        val FAST = MovementProfile("Fast walk", 6.0)
        val PRESETS = listOf(SLOW, NORMAL, BRISK, FAST)

        fun custom(speedKmh: Double) = MovementProfile("Custom", speedKmh)

        fun kmhToMps(kmh: Double): Double = kmh * 1000.0 / 3600.0
        fun mpsToKmh(mps: Double): Double = mps * 3600.0 / 1000.0
    }
}

/** Allowed location publication intervals. */
enum class UpdateInterval(val millis: Long, val label: String) {
    HALF_SECOND(500, "500 ms"),
    ONE_SECOND(1_000, "1 s"),
    TWO_SECONDS(2_000, "2 s"),
    FIVE_SECONDS(5_000, "5 s");

    companion object {
        val DEFAULT = ONE_SECOND
        fun fromMillis(millis: Long): UpdateInterval = entries.firstOrNull { it.millis == millis } ?: DEFAULT
    }
}
