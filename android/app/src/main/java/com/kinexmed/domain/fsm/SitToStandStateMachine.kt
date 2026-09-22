package com.kinexmed.domain.fsm

import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

data class SitToStandRuleConfig(
    val seatedKneeMaxAngle: Double = 105.0,        // Seated baseline flexion
    val standingKneeMinAngle: Double = 155.0,      // Full stand threshold
    val standingHipMinAngle: Double = 150.0,       // Full hip extension
    val minRepDurationMs: Long = 1000L,            // Minimum time for controlled sit-to-stand
    val maxRepDurationMs: Long = 8000L,            // Maximum time before timeout
    val hysteresisDwellFrames: Int = 3,
    val evidenceGracePeriodMs: Long = 800L
)

/**
 * Deterministic finite-state machine for Sit-to-Stand rehabilitation exercise.
 * Cycle:
 * IDLE (user seated) -> START -> RISING (standing up) -> PEAK (standing upright) -> LOWERING (sitting down) -> COMPLETED -> START
 */
class SitToStandStateMachine(
    val config: SitToStandRuleConfig = SitToStandRuleConfig()
) : ExerciseStateMachine {
    override val exerciseType: ExerciseType = ExerciseType.SIT_TO_STAND
    override val primaryMetricName: String = "Knee Extension"
    override val primaryMetricUnit: String = "°"
    override val targetThresholdDesc: String = "Target: ≥${config.standingKneeMinAngle.toInt()}°"

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

    var maxAngleInRep: Double = 90.0
        private set

    var startAngleInRep: Double = 90.0
        private set

    private var candidateState: ExerciseState? = null
    private var candidateDwellCount: Int = 0
    private var prevMetric: Double = 90.0
    private var lastEvidenceFailTimeMs: Long = 0L

    var debugLogger: ((String) -> Unit)? = null

    override fun reset() {
        currentState = ExerciseState.IDLE
        repCounter = 0
        validRepCounter = 0
        repStartTimeMs = 0L
        peakTimeMs = 0L
        maxAngleInRep = 90.0
        startAngleInRep = 90.0
        candidateState = null
        candidateDwellCount = 0
        prevMetric = 90.0
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
        val kneeAngle = smoothedMetric
        val hipAngle = GeometryEngine.getPrimaryHipAngle(landmarks, timestampMs)?.angleDegrees ?: 170.0
        prevMetric = kneeAngle

        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        when (currentState) {
            ExerciseState.IDLE -> {
                feedbackMessage = "Sit in chair with back straight to begin"
                // Transition to START when user is comfortably seated (knee flexed ~90°)
                if (checkDwell(kneeAngle <= config.seatedKneeMaxAngle + 10.0, ExerciseState.START)) {
                    currentState = ExerciseState.START
                    startAngleInRep = kneeAngle
                    maxAngleInRep = kneeAngle
                    feedbackMessage = "Ready. Stand up fully when ready."
                }
            }

            ExerciseState.START -> {
                feedbackMessage = "Ready. Stand all the way up."
                startAngleInRep = kneeAngle
                maxAngleInRep = kneeAngle

                // User begins standing (knee starts extending past seated threshold)
                val isInitiatingStand = kneeAngle >= config.seatedKneeMaxAngle + 8.0
                if (checkDwell(isInitiatingStand, ExerciseState.RISING)) {
                    currentState = ExerciseState.RISING
                    repStartTimeMs = timestampMs
                    maxAngleInRep = kneeAngle
                    feedbackMessage = "Standing up..."
                }
            }

            ExerciseState.RISING -> {
                feedbackMessage = "Stand all the way up (reach ≥${config.standingKneeMinAngle.toInt()}°)"
                if (kneeAngle > maxAngleInRep) {
                    maxAngleInRep = kneeAngle
                }

                // Inflection check: reached standing height or reversed early
                val isStanding = (kneeAngle >= config.standingKneeMinAngle) && (hipAngle >= config.standingHipMinAngle - 10.0)
                val isReversingDownEarly = (kneeAngle <= maxAngleInRep - 5.0) && (kneeAngle < config.standingKneeMinAngle)

                if (isStanding || isReversingDownEarly) {
                    currentState = ExerciseState.PEAK
                    peakTimeMs = timestampMs
                    feedbackMessage = if (isStanding) "Fully upright! Now sit back down with control." else "Sitting back down"
                }
            }

            ExerciseState.PEAK -> {
                feedbackMessage = "Now lower yourself back into the chair"
                if (kneeAngle > maxAngleInRep) {
                    maxAngleInRep = kneeAngle
                }

                val isDescending = kneeAngle <= maxAngleInRep - 6.0 || kneeAngle <= 140.0
                if (isDescending) {
                    currentState = ExerciseState.LOWERING
                    feedbackMessage = "Sitting down with control..."
                }
            }

            ExerciseState.LOWERING -> {
                feedbackMessage = "Lower hips all the way into chair"

                // Check for incomplete return: stood back up before sitting down
                val isStandingAgainEarly = kneeAngle >= prevMetric + 5.0 && kneeAngle > config.seatedKneeMaxAngle + 15.0
                if (isStandingAgainEarly && (timestampMs - repStartTimeMs) >= config.minRepDurationMs) {
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    failureReasons.add("Incomplete return: stood back up before fully sitting in chair (reached ${kneeAngle.toInt()}°, target ≤${config.seatedKneeMaxAngle.toInt()}°)")
                    if (maxAngleInRep < config.standingKneeMinAngle) {
                        failureReasons.add("Insufficient standing extension: reached ${maxAngleInRep.toInt()}°, required ≥${config.standingKneeMinAngle.toInt()}°")
                    }

                    repCounter++
                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = false,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = peakTimeMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = maxAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = kneeAngle,
                        feedbackMessage = "Incomplete return. Sit all the way down between repetitions.",
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.RISING
                    repStartTimeMs = timestampMs
                    maxAngleInRep = kneeAngle
                    startAngleInRep = kneeAngle
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.RISING,
                        currentAngle = kneeAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = "Incomplete return. Sit down fully between repetitions."
                    )
                }

                // Return to seated position
                if (kneeAngle <= config.seatedKneeMaxAngle) {
                    currentState = ExerciseState.COMPLETED
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    var isValid = true

                    if (maxAngleInRep < config.standingKneeMinAngle) {
                        isValid = false
                        failureReasons.add("Insufficient stand: reached ${maxAngleInRep.toInt()}°, required ≥${config.standingKneeMinAngle.toInt()}°")
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
                        feedbackMessage = "Sit-to-stand #$validRepCounter completed!"
                    } else {
                        feedbackMessage = if (maxAngleInRep < config.standingKneeMinAngle) {
                            "Stand fully upright next time. Reached ${maxAngleInRep.toInt()}°."
                        } else {
                            "Move with control."
                        }
                    }

                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = isValid,
                        startTimestampMs = repStartTimeMs,
                        peakTimestampMs = peakTimeMs,
                        endTimestampMs = timestampMs,
                        durationMs = durationMs,
                        peakKneeAngle = maxAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = kneeAngle,
                        feedbackMessage = feedbackMessage,
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.START
                    maxAngleInRep = kneeAngle
                    startAngleInRep = kneeAngle
                    repStartTimeMs = 0L
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.COMPLETED,
                        currentAngle = kneeAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = feedbackMessage
                    )
                }
            }

            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.START
                feedbackMessage = "Ready. Begin next repetition."
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = kneeAngle,
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
