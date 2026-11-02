package com.kinexmed.domain.rules

/**
 * Configurable clinical and biomechanical thresholds for the squat exercise.
 * Placed in an explicit configuration object so they can be calibrated,
 * adjusted per patient prescription, or unit-tested.
 *
 * Biomechanically calibrated for real human movement and 2D landmark projection:
 * - Natural standing knee angle: ~150° - 175°
 * - Parallel squat depth: ~80° - 95°
 * - Valid clinical depth threshold: <= 100°
 */
data class SquatRuleConfig(
    val standingExtensionAngle: Double = 142.0,   // Angle >= 142° confirms standing upright (IDLE -> START)
    val descentStartThreshold: Double = 135.0,    // Angle < 135° initiates descent (START -> LOWERING)
    val targetDepthAngle: Double = 90.0,          // Prescribed clinical target depth
    val minValidDepthAngle: Double = 100.0,       // Must flex to <= 100° for a valid rep
    val ascentStartThreshold: Double = 105.0,     // Angle ascending confirms upward drive (PEAK -> RISING)
    val completionExtensionAngle: Double = 138.0, // Returning to >= 138° completes the repetition (RISING -> COMPLETED)
    val minRepDurationMs: Long = 500L,           // Minimum 500ms (rejects instantaneous twitches)
    val maxRepDurationMs: Long = 10000L,         // Maximum 10s (tolerates slow, controlled rehabilitation reps)
    val hysteresisDwellFrames: Int = 1,          // 1 frame confirmation for responsive tracking
    val evidenceGracePeriodMs: Long = 1500L       // Grace period before resetting in-progress rep on evidence loss
)
