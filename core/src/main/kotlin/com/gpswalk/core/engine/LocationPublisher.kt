package com.gpswalk.core.engine

import com.gpswalk.core.model.SimulatedFix

/**
 * Destination for simulated fixes. The Android implementation injects mock locations;
 * tests use a fake. Implementations must never report success for an injection that failed.
 */
interface LocationPublisher {
    /** Human-readable name of the injection channel(s), for diagnostics. */
    val name: String

    /** Prepare the provider. Returns a failure if mock locations cannot be injected. */
    suspend fun initialize(): PublishResult

    suspend fun publish(fix: SimulatedFix): PublishResult

    /** Release the provider. Must be safe to call more than once. */
    suspend fun stop()

    fun isAvailable(): Boolean
}

sealed interface PublishResult {
    data class Success(val detail: String = "") : PublishResult

    /**
     * @param fatal true when retrying cannot help (e.g. app not selected as mock location app).
     */
    data class Failure(val message: String, val fatal: Boolean, val cause: Throwable? = null) : PublishResult
}
