package com.gpswalk.core.model

/**
 * Simulation lifecycle. Transitions are restricted by [canTransitionTo]; any other move
 * is a programming error and is rejected by the engine.
 */
enum class SimulationState {
    IDLE, READY, RUNNING, PAUSED, COMPLETED, STOPPED, ERROR;

    val isActive: Boolean get() = this == RUNNING || this == PAUSED
    val isTerminal: Boolean get() = this == COMPLETED || this == STOPPED || this == ERROR

    fun canTransitionTo(next: SimulationState): Boolean = next in allowed.getValue(this)

    private companion object {
        val allowed: Map<SimulationState, Set<SimulationState>> = mapOf(
            IDLE to setOf(READY),
            READY to setOf(READY, RUNNING, IDLE, ERROR),
            RUNNING to setOf(PAUSED, COMPLETED, STOPPED, ERROR),
            PAUSED to setOf(RUNNING, STOPPED, ERROR),
            COMPLETED to setOf(READY, IDLE),
            STOPPED to setOf(READY, IDLE),
            ERROR to setOf(READY, IDLE),
        )
    }
}
