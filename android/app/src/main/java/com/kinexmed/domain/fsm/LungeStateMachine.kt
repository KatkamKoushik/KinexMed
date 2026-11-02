package com.kinexmed.domain.fsm

import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

data class LungeRuleConfig(
    val standingExtensionAngle: Double = 155.0,    // Upright start/finish
    val descentStartThreshold: Double = 145.0,     // Knee begins flexing
    val minValidDepthAngle: Double = 100.0,        // Valid lunge depth target
    val targetDepthAngle: Double = 90.0,           // Optimal 90° lunge
    val completionExtensionAngle: Double = 150.0,  // Return to standing
    val minRepDurationMs: Long = 1000L,
    val maxRepDurationMs: Long = 7000L,
    val hysteresisDwellFrames: Int = 3,
    val evidenceGracePeriodMs: Long = 800L
)

/**
 * Deterministic finite-state machine for Forward / Reverse Lunge analysis.
 * Cycle:
 * IDLE -> START -> LOWERING -> PEAK -> RISING -> COMPLETED -> START
 */
class LungeStateMachine(
    val config: LungeRuleConfig = LungeRuleConfig()
) : ExerciseStateMachine {
    override val exerciseType: ExerciseType = ExerciseType.FORWARD_LUNGE
    override val primaryMetricName: String = "Lead Knee Angle"
    override val primaryMetricUnit: String = "°"
    override val targetThresholdDesc: String = "Target: ≤${config.minValidDepthAngle.toInt()}°"

    override var currentState: ExerciseState = ExerciseState.IDLE
        private set

    override var repCounter: Int = 0
        private set

    override var validRepCounter: Int = 0
        private set

    var repStartTimeMs: Long = 0L
        private set

    var peakTimeMs: Long = 0L
        private set

    var minAngleInRep: Double = 180.0
        private set

    var startAngleInRep: Double = 180.0
        private set

    private var candidateState: ExerciseState? = null
    private var candidateDwellCount: Int = 0
    private var prevMetric: Double = 180.0
    private var lastEvidenceFailTimeMs: Long = 0L

    var debugLogger: ((String) -> Unit)? = null

    override fun reset() {
        currentState = ExerciseState.IDLE
        repCounter = 0
        validRepCounter = 0
        repStartTimeMs = 0L
        peakTimeMs = 0L
        minAngleInRep = 180.0
        startAngleInRep = 180.0
        candidateState = null
        candidateDwellCount = 0
        prevMetric = 180.0
        lastEvidenceFailTimeMs = 0L
    }

    override fun update(
        landmarks: Map<Int, Landmark>,
        smoothedMetric: Double,
        timestampMs: Long,
        evidenceStatus: EvidenceStatus
    ): FsmUpdateResult {
        if (evidenceStatus is EvidenceStatus.Insufficient) {
            if (lastEvidenceFailTimeMs == 0L) {
                lastEvidenceFailTimeMs = timestampMs
            } else if (timestampMs - lastEvidenceFailTimeMs > config.evidenceGracePeriodMs && currentState != ExerciseState.IDLE) {
                currentState = ExerciseState.IDLE
                candidateState = null
                candidateDwellCount = 0
                repStartTimeMs = 0L
                peakTimeMs = 0L
            }
            return FsmUpdateResult(
                state = currentState,
                currentAngle = smoothedMetric,
                completedRep = null,
                totalReps = repCounter,
                validReps = validRepCounter,
                feedbackMessage = evidenceStatus.userMessage
            )
        }

        lastEvidenceFailTimeMs = 0L
        // Use lead knee angle (deeper flexed knee)
        val leadKneeAngle = smoothedMetric
        prevMetric = leadKneeAngle

        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        when (currentState) {
            ExerciseState.IDLE -> {
                feedbackMessage = "Stand upright with feet shoulder-width apart"
                if (checkDwell(leadKneeAngle >= config.standingExtensionAngle, ExerciseState.START)) {
                    currentState = ExerciseState.START
                    startAngleInRep = leadKneeAngle
                    minAngleInRep = leadKneeAngle
                    feedbackMessage = "Ready. Step forward or backward into a lunge."
                }
            }

            ExerciseState.START -> {
                feedbackMessage = "Step into a controlled lunge."
                startAngleInRep = leadKneeAngle
                minAngleInRep = leadKneeAngle

                val isInitiatingLunge = leadKneeAngle < config.descentStartThreshold
                if (checkDwell(isInitiatingLunge, ExerciseState.LOWERING)) {
                    currentState = ExerciseState.LOWERING
                    repStartTimeMs = timestampMs
                    minAngleInRep = leadKneeAngle
                    feedbackMessage = "Lowering into lunge..."
                }
            }

            ExerciseState.LOWERING -> {
                feedbackMessage = "Lower hips until front knee reaches ~${config.targetDepthAngle.toInt()}°"
                if (leadKneeAngle < minAngleInRep) {
                    minAngleInRep = leadKneeAngle
                }

                val isDeepEnough = minAngleInRep <= config.minValidDepthAngle
                val hasStartedRising = leadKneeAngle >= minAngleInRep + 3.5
                val isShallowReversal = !isDeepEnough && (leadKneeAngle >= minAngleInRep + 4.5)

                if (hasStartedRising || isShallowReversal) {
                    currentState = ExerciseState.PEAK
                    peakTimeMs = timestampMs
                    feedbackMessage = if (isDeepEnough) "Good depth (${minAngleInRep.toInt()}°)! Push back up." else "Rising back up"
                }
            }

            ExerciseState.PEAK -> {
                feedbackMessage = "Push through front heel to return to standing"
                if (leadKneeAngle < minAngleInRep) {
                    minAngleInRep = leadKneeAngle
                }

                val isAscending = leadKneeAngle >= minAngleInRep + 4.0
                if (isAscending) {
                    currentState = ExerciseState.RISING
                    feedbackMessage = "Returning to stand..."
                }
            }

            ExerciseState.RISING -> {
                feedbackMessage = "Stand all the way back up"

                // Check for incomplete return
                val isLungingAgainEarly = leadKneeAngle <= prevMetric - 3.5 && leadKneeAngle < config.descentStartThreshold
                if (isLungingAgainEarly && (timestampMs - repStartTimeMs) >= config.minRepDurationMs) {
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    failureReasons.add("Incomplete return: started next lunge before fully returning to upright standing (reached ${leadKneeAngle.toInt()}°, target ≥${config.completionExtensionAngle.toInt()}°)")
                    if (minAngleInRep > config.minValidDepthAngle) {
                        failureReasons.add("Insufficient depth: reached ${minAngleInRep.toInt()}°, target was ≤${config.minValidDepthAngle.toInt()}°")
                    }

                    repCounter++
                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = false,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = peakTimeMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = minAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = leadKneeAngle,
                        feedbackMessage = "Incomplete return. Stand fully upright between lunges.",
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.LOWERING
                    repStartTimeMs = timestampMs
                    minAngleInRep = leadKneeAngle
                    startAngleInRep = leadKneeAngle
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.LOWERING,
                        currentAngle = leadKneeAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = "Incomplete return. Return to standing fully between lunges."
                    )
                }

                // Return to standing
                if (leadKneeAngle >= config.completionExtensionAngle) {
                    currentState = ExerciseState.COMPLETED
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    var isValid = true

                    if (minAngleInRep > config.minValidDepthAngle) {
                        isValid = false
                        failureReasons.add("Insufficient depth: reached ${minAngleInRep.toInt()}°, required ≤${config.minValidDepthAngle.toInt()}°")
                    }

                    if (durationMs < config.minRepDurationMs) {
                        isValid = false
                        failureReasons.add("Repetition too fast (${durationMs}ms), maintain control")
                    } else if (durationMs > config.maxRepDurationMs) {
                        isValid = false
                        failureReasons.add("Repetition duration exceeded limit (${durationMs / 1000}s)")
                    }

                    repCounter++
                    if (isValid) {
                        validRepCounter++
                        feedbackMessage = "Lunge #$validRepCounter completed. Good depth!"
                    } else {
                        feedbackMessage = if (minAngleInRep > config.minValidDepthAngle) {
                            "Lunge deeper next time. Reached ${minAngleInRep.toInt()}°."
                        } else {
                            "Repetition too fast. Move with control."
                        }
                    }

                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = isValid,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = peakTimeMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = minAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = leadKneeAngle,
                        feedbackMessage = feedbackMessage,
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.START
                    minAngleInRep = leadKneeAngle
                    startAngleInRep = leadKneeAngle
                    repStartTimeMs = 0L
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.COMPLETED,
                        currentAngle = leadKneeAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = feedbackMessage
                    )
                }
            }

            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.START
                feedbackMessage = "Ready. Begin next lunge."
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = leadKneeAngle,
            completedRep = completedRep,
            totalReps = repCounter,
            validReps = validRepCounter,
            feedbackMessage = feedbackMessage
        )
    }

    private fun checkDwell(condition: Boolean, targetState: ExerciseState): Boolean {
        if (condition) {
            if (candidateState == targetState) {
                candidateDwellCount++
            } else {
                candidateState = targetState
                candidateDwellCount = 1
            }
            if (candidateDwellCount >= config.hysteresisDwellFrames) {
                candidateState = null
                candidateDwellCount = 0
                return true
            }
        } else {
            if (candidateState == targetState) {
                candidateState = null
                candidateDwellCount = 0
            }
        }
        return false
    }
}
