package com.gpswalk.simulator.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    fun distance(m: Double): String = if (m >= 1000) "%.2f km".format(Locale.US, m / 1000) else "%.1f m".format(Locale.US, m)
    fun speed(kmh: Double): String = "%.1f km/h".format(Locale.US, kmh)
    fun coord(v: Double): String = "%.6f".format(Locale.US, v)
    fun duration(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(Locale.US, s / 3600, (s / 60) % 60, s % 60)
    }
    fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}
