package com.kinexmed.domain.fsm

import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

data class CalfRaiseRuleConfig(
    val flatFootMaxAngle: Double = 100.0,          // Flat on floor neutral ankle angle
    val liftStartThreshold: Double = 108.0,        // Begins rising onto toes
    val minValidPlantarflexionAngle: Double = 120.0, // Minimum valid heel rise
    val targetPlantarflexionAngle: Double = 130.0, // Optimal calf raise peak
    val completionFlatAngle: Double = 102.0,       // Returned flat to floor
    val minRepDurationMs: Long = 800L,
    val maxRepDurationMs: Long = 6000L,
    val hysteresisDwellFrames: Int = 3,
    val evidenceGracePeriodMs: Long = 800L
)

/**
 * Deterministic finite-state machine for Calf Raise analysis.
 * Primary metric: Ankle plantarflexion (Knee -> Ankle -> Foot Index angle).
 * Cycle:
 * IDLE -> START -> RISING (lifting heels) -> PEAK -> LOWERING (returning to flat) -> COMPLETED -> START
 */
class CalfRaiseStateMachine(
    val config: CalfRaiseRuleConfig = CalfRaiseRuleConfig()
) : ExerciseStateMachine {
    override val exerciseType: ExerciseType = ExerciseType.CALF_RAISE
    override val primaryMetricName: String = "Ankle Angle"
    override val primaryMetricUnit: String = "°"
    override val targetThresholdDesc: String = "Target: ≥${config.minValidPlantarflexionAngle.toInt()}°"

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
        val ankleAngle = smoothedMetric
        prevMetric = ankleAngle

        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        when (currentState) {
            ExerciseState.IDLE -> {
                feedbackMessage = "Stand upright with feet flat on the floor"
                if (checkDwell(ankleAngle <= config.flatFootMaxAngle + 5.0, ExerciseState.START)) {
                    currentState = ExerciseState.START
                    startAngleInRep = ankleAngle
                    maxAngleInRep = ankleAngle
                    feedbackMessage = "Ready. Raise up onto the balls of your feet."
                }
            }

            ExerciseState.START -> {
                feedbackMessage = "Raise heels as high as comfortable."
                startAngleInRep = ankleAngle
                maxAngleInRep = ankleAngle

                val isLifting = ankleAngle >= config.liftStartThreshold
                if (checkDwell(isLifting, ExerciseState.RISING)) {
                    currentState = ExerciseState.RISING
                    repStartTimeMs = timestampMs
                    maxAngleInRep = ankleAngle
                    feedbackMessage = "Lifting heels..."
                }
            }

            ExerciseState.RISING -> {
                feedbackMessage = "Lift high onto toes (reach ≥${config.minValidPlantarflexionAngle.toInt()}°)"
                if (ankleAngle > maxAngleInRep) {
                    maxAngleInRep = ankleAngle
                }

                val isHighEnough = maxAngleInRep >= config.minValidPlantarflexionAngle
                val hasStartedLowering = ankleAngle <= maxAngleInRep - 3.0
                val isShallowReversal = !isHighEnough && (ankleAngle <= maxAngleInRep - 4.0)

                if (hasStartedLowering || isShallowReversal) {
                    currentState = ExerciseState.PEAK
                    peakTimeMs = timestampMs
                    feedbackMessage = if (isHighEnough) "Good heel lift (${maxAngleInRep.toInt()}°)! Lower slowly." else "Lowering back down"
                }
            }

            ExerciseState.PEAK -> {
                feedbackMessage = "Lower heels smoothly back to floor"
                if (ankleAngle > maxAngleInRep) {
                    maxAngleInRep = ankleAngle
                }

                val isLowering = ankleAngle <= maxAngleInRep - 4.0
                if (isLowering) {
                    currentState = ExerciseState.LOWERING
                    feedbackMessage = "Lowering heels..."
                }
            }

            ExerciseState.LOWERING -> {
                feedbackMessage = "Lower all the way until feet are flat"

                // Check for incomplete return: lifted again before heels touched ground
                val isLiftingAgainEarly = ankleAngle >= prevMetric + 4.0 && ankleAngle > config.flatFootMaxAngle + 10.0
                if (isLiftingAgainEarly && (timestampMs - repStartTimeMs) >= config.minRepDurationMs) {
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    failureReasons.add("Incomplete return: raised heels again before fully touching floor (reached ${ankleAngle.toInt()}°, required ≤${config.completionFlatAngle.toInt()}°)")
                    if (maxAngleInRep < config.minValidPlantarflexionAngle) {
                        failureReasons.add("Insufficient heel lift: reached ${maxAngleInRep.toInt()}°, target was ≥${config.minValidPlantarflexionAngle.toInt()}°")
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
                        endKneeAngle = ankleAngle,
                        feedbackMessage = "Incomplete return. Place feet flat between calf raises.",
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.RISING
                    repStartTimeMs = timestampMs
                    maxAngleInRep = ankleAngle
                    startAngleInRep = ankleAngle
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.RISING,
                        currentAngle = ankleAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = "Incomplete return. Place feet flat between calf raises."
                    )
                }

                // Return to flat ground
                if (ankleAngle <= config.completionFlatAngle) {
                    currentState = ExerciseState.COMPLETED
                    val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                    val failureReasons = mutableListOf<String>()
                    var isValid = true

                    if (maxAngleInRep < config.minValidPlantarflexionAngle) {
                        isValid = false
                        failureReasons.add("Insufficient heel lift: reached ${maxAngleInRep.toInt()}°, required ≥${config.minValidPlantarflexionAngle.toInt()}°")
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
                        feedbackMessage = "Calf raise #$validRepCounter completed!"
                    } else {
                        feedbackMessage = if (maxAngleInRep < config.minValidPlantarflexionAngle) {
                            "Lift higher next time. Reached ${maxAngleInRep.toInt()}°."
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
                        peakKneeAngle = maxAngleInRep,
                        startKneeAngle = startAngleInRep,
                        endKneeAngle = ankleAngle,
                        feedbackMessage = feedbackMessage,
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.START
                    maxAngleInRep = ankleAngle
                    startAngleInRep = ankleAngle
                    repStartTimeMs = 0L
                    peakTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.COMPLETED,
                        currentAngle = ankleAngle,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = feedbackMessage
                    )
                }
            }

            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.START
                feedbackMessage = "Ready. Begin next calf raise."
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = ankleAngle,
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
