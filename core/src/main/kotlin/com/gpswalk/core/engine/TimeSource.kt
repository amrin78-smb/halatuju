package com.gpswalk.core.engine

/**
 * Time abstraction so movement uses a monotonic clock and tests can use virtual time.
 * On Android: monotonic = SystemClock.elapsedRealtimeNanos(), wall = System.currentTimeMillis().
 */
interface TimeSource {
    fun monotonicNanos(): Long
    fun wallClockMillis(): Long
}

object SystemTimeSource : TimeSource {
    override fun monotonicNanos(): Long = System.nanoTime()
    override fun wallClockMillis(): Long = System.currentTimeMillis()
}
