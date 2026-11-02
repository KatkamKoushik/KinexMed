package com.kinexmed.domain.fsm

import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.rules.SquatRuleConfig

/**
 * Output of a state machine tick update.
 */
data class FsmUpdateResult(
    val state: ExerciseState,
    val currentAngle: Double,
    val completedRep: RepRecord? = null,
    val totalReps: Int,
    val validReps: Int,
    val feedbackMessage: String
)

/**
 * Deterministic finite-state machine (FSM) for Squat analysis.
 *
 * State flow:
 * IDLE -> START -> LOWERING -> PEAK -> RISING -> COMPLETED -> START
 *
 * Designed for human movement kinematics:
 * - Dwell debouncing at postural boundaries (IDLE -> START and START -> LOWERING) eliminates noise spikes.
 * - Geometric inflection detection (LOWERING -> PEAK -> RISING) tracks the reversal of direction without
 *   excessive dwell delays that drop fast or normal human squats.
 * - Single-frame rep completion transition directly resets to START, guaranteeing no duplicate reps.
 * - Configurable grace period protects in-progress reps from transient single-frame evidence drops.
 */
class SquatStateMachine(
    val config: SquatRuleConfig = SquatRuleConfig()
) : ExerciseStateMachine {
    override val exerciseType: com.kinexmed.domain.model.ExerciseType = com.kinexmed.domain.model.ExerciseType.SQUAT
    override val primaryMetricName: String = "Knee Angle"
    override val primaryMetricUnit: String = "°"
    override val targetThresholdDesc: String = "Target: ≤${config.minValidDepthAngle.toInt()}°"

    override var currentState: ExerciseState = ExerciseState.IDLE
        private set

    override var repCounter: Int = 0
        private set

    override var validRepCounter: Int = 0
        private set

    // Repetition tracking variables
    var repStartTimeMs: Long = 0L
        private set

    var peakTimeMs: Long = 0L
        private set

    var minAngleInRep: Double = 180.0
        private set

    var startAngleInRep: Double = 180.0
        private set

    // Debouncing / Hysteresis dwell counter
    var candidateState: ExerciseState? = null
        private set

    var candidateDwellCount: Int = 0
        private set

    // Previous angle for velocity and direction detection
    var prevAngle: Double = 180.0
        private set

    private var lastEvidenceFailTimeMs: Long = 0L

    // Optional logger for inspecting state progression without UI spam
    var debugLogger: ((String) -> Unit)? = null

    /**
     * Resets the state machine for a new exercise session.
     */
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
        prevAngle = 180.0
        lastEvidenceFailTimeMs = 0L
    }

    override fun update(
        landmarks: Map<Int, com.kinexmed.domain.model.Landmark>,
        smoothedMetric: Double,
        timestampMs: Long,
        evidenceStatus: EvidenceStatus
    ): FsmUpdateResult {
        return update(smoothedMetric, timestampMs, evidenceStatus)
    }

    /**
     * Processes a new frame with smoothed knee angle, timestamp, and visual evidence status.
     *
     * Invariant: If evidence is insufficient, form is NOT evaluated and reps are NOT counted.
     * A short grace period ([SquatRuleConfig.evidenceGracePeriodMs]) prevents transient
     * single-frame occlusions from resetting an active rep prematurely.
     */
    fun update(
        smoothedAngle: Double,
        timestampMs: Long,
        evidenceStatus: EvidenceStatus
    ): FsmUpdateResult {
        // If evidence fails, freeze progress and report repositioning guidance
        if (evidenceStatus is EvidenceStatus.Insufficient) {
            if (lastEvidenceFailTimeMs == 0L) {
                lastEvidenceFailTimeMs = timestampMs
            } else if (timestampMs - lastEvidenceFailTimeMs > config.evidenceGracePeriodMs && currentState != ExerciseState.IDLE) {
                // Prolonged evidence loss: safely reset ongoing partial repetition to IDLE
                // Existing completed reps and validRepCounter remain preserved in the session
                debugLogger?.invoke("Evidence lost for ${timestampMs - lastEvidenceFailTimeMs}ms. Resetting to IDLE.")
                currentState = ExerciseState.IDLE
                candidateState = null
                candidateDwellCount = 0
                repStartTimeMs = 0L
                peakTimeMs = 0L
                minAngleInRep = 180.0
            }

            return FsmUpdateResult(
                state = currentState,
                currentAngle = smoothedAngle,
                completedRep = null,
                totalReps = repCounter,
                validReps = validRepCounter,
                feedbackMessage = evidenceStatus.userMessage
            )
        }

        // Evidence is sufficient: clear evidence loss timer
        lastEvidenceFailTimeMs = 0L
        val lastAngle = prevAngle
        val angleDelta = smoothedAngle - lastAngle // positive = extending, negative = flexing
        prevAngle = smoothedAngle

        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        debugLogger?.invoke(
            "Frame: angle=${String.format("%.1f", smoothedAngle)}°, state=$currentState, " +
                    "minAngle=${String.format("%.1f", minAngleInRep)}°, dwell=$candidateDwellCount"
        )

        when (currentState) {
            ExerciseState.IDLE -> {
                feedbackMessage = "Stand upright to begin exercise"
                // Transition to START when user stands upright with knees extended
                if (checkDwell(smoothedAngle >= config.standingExtensionAngle, ExerciseState.START)) {
                    currentState = ExerciseState.START
                    startAngleInRep = smoothedAngle
                    minAngleInRep = smoothedAngle
                    feedbackMessage = "Ready. Begin squatting down slowly."
                    debugLogger?.invoke("Transition: IDLE -> START at angle $smoothedAngle°")
                }
            }

            ExerciseState.START -> {
                feedbackMessage = "Ready. Begin squatting down."
                if (smoothedAngle > startAngleInRep) {
                    startAngleInRep = smoothedAngle
                }
                minAngleInRep = smoothedAngle

                // Transition to LOWERING when knee flexes below descentStartThreshold
                val isInitiatingDescent = smoothedAngle < config.descentStartThreshold

                if (checkDwell(isInitiatingDescent, ExerciseState.LOWERING)) {
                    currentState = ExerciseState.LOWERING
                    repStartTimeMs = timestampMs
                    minAngleInRep = smoothedAngle
                    feedbackMessage = "Lowering down..."
                    debugLogger?.invoke("Transition: START -> LOWERING at angle $smoothedAngle°")
                }
            }

            ExerciseState.LOWERING -> {
                feedbackMessage = "Lowering: reach ~${config.targetDepthAngle.toInt()}°"
                if (smoothedAngle < minAngleInRep) {
                    minAngleInRep = smoothedAngle
                }

                // Geometric inflection detection (bottom of descent)
                val isDeepEnough = minAngleInRep <= config.minValidDepthAngle
                val hasStartedRising = smoothedAngle >= minAngleInRep + 3.0
                val isDeepPause = isDeepEnough && (smoothedAngle <= config.targetDepthAngle && angleDelta >= 0.0)
                val isBottomInflection = isDeepEnough && (hasStartedRising || isDeepPause)

                // Shallow squat reversal: stopped descending early and reversed upward by at least 4°
                val isShallowReversal = !isDeepEnough && (smoothedAngle >= minAngleInRep + 4.0)

                if (isBottomInflection || isShallowReversal) {
                    currentState = ExerciseState.PEAK
                    candidateState = null
                    candidateDwellCount = 0
                    peakTimeMs = timestampMs
                    feedbackMessage = if (isDeepEnough) {
                        "Good depth reached (${minAngleInRep.toInt()}°)! Rise up."
                    } else {
                        "Rising up (shallow depth)"
                    }
                    debugLogger?.invoke("Transition: LOWERING -> PEAK at minAngle $minAngleInRep°")
                }
            }

            ExerciseState.PEAK -> {
                feedbackMessage = "Peak depth: ${minAngleInRep.toInt()}°. Push through heels to rise."
                if (smoothedAngle < minAngleInRep) {
                    minAngleInRep = smoothedAngle
                }

                // Transition to RISING when knee angle actively extends upward
                val isAscending = (smoothedAngle >= minAngleInRep + 4.0) || (smoothedAngle >= config.ascentStartThreshold)

                if (isAscending) {
                    currentState = ExerciseState.RISING
                    candidateState = null
                    candidateDwellCount = 0
                    feedbackMessage = "Rising up..."
                    debugLogger?.invoke("Transition: PEAK -> RISING at angle $smoothedAngle°")
                }
            }

            ExerciseState.RISING -> {
                feedbackMessage = "Stand all the way up to complete rep"

                // Check for incomplete return: reversed downward before fully standing upright
                val isReversingDownward = (smoothedAngle <= lastAngle - 4.5) && (smoothedAngle < config.descentStartThreshold - 10.0)
                if (isReversingDownward && (timestampMs - repStartTimeMs) >= config.minRepDurationMs) {
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    failureReasons.add("Incomplete return: started next repetition before fully standing upright (reached ${smoothedAngle.toInt()}°, required ≥${config.completionExtensionAngle.toInt()}°)")
                    if (minAngleInRep > config.minValidDepthAngle) {
                        failureReasons.add("Insufficient depth: reached ${minAngleInRep.toInt()}°, target was ≤${config.minValidDepthAngle.toInt()}°")
                    }

                    repCounter++
                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = false,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = if (peakTimeMs > 0L) peakTimeMs else timestampMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = minAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = smoothedAngle,
                        feedbackMessage = "Incomplete return. Stand fully upright between squats.",
                        failureReasons = failureReasons
                    )

                    debugLogger?.invoke("Rep #$repCounter rejected for incomplete return at angle ${smoothedAngle.toInt()}°")

                    // Transition directly to LOWERING for the new descent
                    currentState = ExerciseState.LOWERING
                    repStartTimeMs = timestampMs
                    minAngleInRep = smoothedAngle
                    startAngleInRep = smoothedAngle
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.LOWERING,
                        currentAngle = smoothedAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = "Incomplete return. Stand fully upright between squats."
                    )
                }

                // Transition to COMPLETED when returned to standing extension
                if (smoothedAngle >= config.completionExtensionAngle) {
                    currentState = ExerciseState.COMPLETED
                    candidateState = null
                    candidateDwellCount = 0

                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    var isValid = true

                    if (minAngleInRep > config.minValidDepthAngle) {
                        isValid = false
                        failureReasons.add("Insufficient depth: reached ${minAngleInRep.toInt()}°, target was ${config.minValidDepthAngle.toInt()}°")
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
                        feedbackMessage = "Rep $validRepCounter completed. Good depth!"
                    } else {
                        feedbackMessage = if (minAngleInRep > config.minValidDepthAngle) {
                            "Squat deeper next time. Reached ${minAngleInRep.toInt()}°."
                        } else {
                            "Repetition too fast. Move with control."
                        }
                    }

                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = isValid,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = if (peakTimeMs > 0L) peakTimeMs else timestampMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = minAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = smoothedAngle,
                        feedbackMessage = feedbackMessage,
                        failureReasons = failureReasons
                    )

                    debugLogger?.invoke("Rep completed: #$repCounter, valid=$isValid, depth=${minAngleInRep.toInt()}°, duration=${durationMs}ms")

                    // Transition state immediately back to START ready for next repetition
                    // This strictly prevents duplicate rep creation on subsequent frames
                    currentState = ExerciseState.START
                    minAngleInRep = smoothedAngle
                    startAngleInRep = smoothedAngle
                    repStartTimeMs = 0L
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.COMPLETED,
                        currentAngle = smoothedAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = feedbackMessage
                    )
                }
            }

            ExerciseState.COMPLETED -> {
                // Failsafe fallback: ready for next repetition
                currentState = ExerciseState.START
                feedbackMessage = "Ready. Begin next repetition."
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = smoothedAngle,
            completedRep = completedRep,
            totalReps = repCounter,
            validReps = validRepCounter,
            feedbackMessage = feedbackMessage
        )
    }

    /**
     * Dwell debouncer: requires candidate condition to be sustained for [SquatRuleConfig.hysteresisDwellFrames]
     * consecutive ticks to eliminate false transitions caused by momentary sensor noise.
     * Resets candidate count immediately if condition evaluates to false.
     */
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
