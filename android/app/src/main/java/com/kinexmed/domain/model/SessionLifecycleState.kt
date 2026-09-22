package com.kinexmed.domain.model

/**
 * Strict, deterministic session lifecycle states for KinexMed exercise sessions.
 *
 * Allowed transitions:
 * IDLE -> PREPARING -> ACTIVE -> PAUSED -> ACTIVE -> FINISHING -> COMPLETED
 * Cancellation: PREPARING/ACTIVE/PAUSED -> CANCELLED
 *
 * Invariants:
 * - Sessions never auto-start or auto-restart.
 * - Rep counting and active timers only run in ACTIVE.
 * - Camera and pose preview may run in IDLE for patient framing without counting reps.
 */
enum class SessionLifecycleState {
    IDLE,
    PREPARING,
    ACTIVE,
    PAUSED,
    FINISHING,
    COMPLETED,
    CANCELLED
}
