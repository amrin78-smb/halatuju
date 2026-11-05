package com.gpswalk.simulator.location

import com.gpswalk.core.engine.LocationPublisher
import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.model.SimulatedFix
import com.gpswalk.simulator.util.AppLog

/**
 * Publishes to several channels. The first channel is the primary one: if it cannot be
 * initialized, or fails fatally, the whole publisher fails. Secondary channels that fail to
 * initialize are dropped and reported; their per-fix failures are reported in the detail
 * string but never hidden behind a plain "OK".
 */
class CompositeLocationPublisher(
    private val primary: LocationPublisher,
    private val secondary: List<LocationPublisher>,
) : LocationPublisher {
    private val active = mutableListOf<LocationPublisher>()
    private val initNotes = mutableListOf<String>()

    override val name: String =
        (listOf(primary) + secondary).joinToString(" + ") { it.name }

    override fun isAvailable(): Boolean = primary.isAvailable()

    override suspend fun initialize(): PublishResult {
        active.clear(); initNotes.clear()
        when (val r = primary.initialize()) {
            is PublishResult.Failure -> return r
            is PublishResult.Success -> active += primary
        }
        for (channel in secondary) {
            when (val r = channel.initialize()) {
                is PublishResult.Success -> active += channel
                is PublishResult.Failure -> {
                    AppLog.w("Secondary channel '${channel.name}' disabled: ${r.message}")
                    initNotes += "${channel.name} disabled: ${r.message}"
                }
            }
        }
        return PublishResult.Success(active.joinToString(" + ") { it.name })
    }

    val initializationNotes: List<String> get() = initNotes.toList()

    override suspend fun publish(fix: SimulatedFix): PublishResult {
        val details = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (channel in active.toList()) {
            when (val r = channel.publish(fix)) {
                is PublishResult.Success -> details += r.detail
                is PublishResult.Failure -> {
                    if (channel === primary) return r
                    if (r.fatal) {
                        AppLog.w("Dropping secondary channel '${channel.name}' after fatal failure: ${r.message}")
                        active.remove(channel)
                        channel.stop()
                    }
                    failures += r.message
                }
            }
        }
        val detail = details.joinToString("+") +
            if (failures.isEmpty()) "" else "; secondary failed: ${failures.joinToString("; ")}"
        return PublishResult.Success(detail)
    }

    override suspend fun stop() {
        for (channel in active.toList()) channel.stop()
        active.clear()
    }
}
