package com.kinexmed.domain.fsm

import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

data class SitToStandRuleConfig(
    val seatedKneeMaxAngle: Double = 115.0,        // Seated baseline flexion
    val standingKneeMinAngle: Double = 148.0,      // Full stand threshold
    val standingHipMinAngle: Double = 145.0,       // Full hip extension
    val minRepDurationMs: Long = 700L,             // Minimum time for controlled sit-to-stand
    val maxRepDurationMs: Long = 10000L,           // Maximum time before timeout
    val evidenceGracePeriodMs: Long = 1000L
)

/**
 * Deterministic finite-state machine for Sit-to-Stand rehabilitation exercise.
 * Optimized for low latency with zero artificial dwell buffers.
 * Supports both seated-start and stand-start cycles.
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
                feedbackMessage = "Sit in chair to begin"
                // Instant zero-delay transition to START once user is seated
                if (kneeAngle <= config.seatedKneeMaxAngle + 5.0) {
                    currentState = ExerciseState.START
                    startAngleInRep = kneeAngle
                    maxAngleInRep = kneeAngle
                    feedbackMessage = "Ready. Stand up fully."
                }
            }

            ExerciseState.START -> {
                feedbackMessage = "Ready. Stand all the way up."
                startAngleInRep = kneeAngle
                maxAngleInRep = kneeAngle

                // User begins standing (knee extends past seated threshold or gains +6 deg velocity delta)
                val isInitiatingStand = (kneeAngle >= config.seatedKneeMaxAngle + 5.0) || (kneeAngle >= startAngleInRep + 6.0)
                if (isInitiatingStand) {
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

                val isStanding = (kneeAngle >= config.standingKneeMinAngle) && (hipAngle >= config.standingHipMinAngle - 15.0)
                val isReversingDownEarly = (kneeAngle <= maxAngleInRep - 4.5) && (kneeAngle < config.standingKneeMinAngle)

                if (isStanding || isReversingDownEarly) {
                    currentState = ExerciseState.PEAK
                    peakTimeMs = timestampMs
                    feedbackMessage = if (isStanding) "Fully upright! Now sit back down." else "Sitting back down"
                }
            }

            ExerciseState.PEAK -> {
                feedbackMessage = "Now lower yourself back into the chair"
                if (kneeAngle > maxAngleInRep) {
                    maxAngleInRep = kneeAngle
                }

                val isDescending = kneeAngle <= maxAngleInRep - 4.0 || kneeAngle <= 140.0
                if (isDescending) {
                    currentState = ExerciseState.LOWERING
                    feedbackMessage = "Sitting down with control..."
                }
            }

            ExerciseState.LOWERING -> {
                feedbackMessage = "Lower hips all the way into chair"

                // Return to seated position completes rep
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
                        failureReasons.add("Repetition duration exceeded limit")
                    }

                    repCounter++
                    if (isValid) {
                        validRepCounter++
                        feedbackMessage = "Sit-to-stand #$validRepCounter complete! Great stand."
                    } else {
                        feedbackMessage = if (maxAngleInRep < config.standingKneeMinAngle) {
                            "Stand fully upright next rep (reached ${maxAngleInRep.toInt()}°)."
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
}
