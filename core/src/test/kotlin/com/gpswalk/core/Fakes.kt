package com.gpswalk.core

import com.gpswalk.core.engine.LocationPublisher
import com.gpswalk.core.engine.PublishResult
import com.gpswalk.core.engine.TimeSource
import com.gpswalk.core.model.SimulatedFix
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Manually advanced clock. */
class FakeTime(var nanos: Long = 1_000_000_000L, var wall: Long = 1_700_000_000_000L) : TimeSource {
    override fun monotonicNanos() = nanos
    override fun wallClockMillis() = wall
    fun advanceMillis(ms: Long) { nanos += ms * 1_000_000; wall += ms }
}

/** Clock driven by the coroutine test scheduler's virtual time. */
class SchedulerTime(private val scheduler: TestCoroutineScheduler) : TimeSource {
    override fun monotonicNanos() = scheduler.currentTime * 1_000_000
    override fun wallClockMillis() = 1_700_000_000_000L + scheduler.currentTime
}

/** Records fixes; can be scripted to fail. */
class FakePublisher(
    var initResult: PublishResult = PublishResult.Success(),
    var failWith: PublishResult.Failure? = null,
) : LocationPublisher {
    override val name = "Fake"
    val published = mutableListOf<SimulatedFix>()
    var stopCalls = 0
    var initCalls = 0
    override suspend fun initialize(): PublishResult { initCalls++; return initResult }
    override suspend fun publish(fix: SimulatedFix): PublishResult {
        failWith?.let { return it }
        published += fix
        return PublishResult.Success()
    }
    override suspend fun stop() { stopCalls++ }
    override fun isAvailable() = true
}
