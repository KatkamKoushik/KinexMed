package com.kinexmed.domain.fsm

import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

/**
 * Deterministic state machine for static balance hold assessments (e.g. Single-Leg Balance).
 * Measures continuous stability duration in seconds.
 */
class BalanceHoldFsm(
    val targetHoldSeconds: Double = 10.0,
    val elevationThreshold: Double = 8.0 // % vertical ankle difference confirming single-leg stance
) : ExerciseStateMachine {

    override val exerciseType: ExerciseType = ExerciseType.SINGLE_LEG_BALANCE
    override val primaryMetricName: String = "Balance Hold"
    override val primaryMetricUnit: String = "s"
    override val targetThresholdDesc: String = "Target: ≥${targetHoldSeconds.toInt()}s"

    override var currentState: ExerciseState = ExerciseState.IDLE
        private set

    override var repCounter: Int = 0
        private set

    override var validRepCounter: Int = 0
        private set

    var holdStartTimeMs: Long = 0L
        private set

    var currentHoldSeconds: Double = 0.0
        private set

    var maxHoldSecondsInRep: Double = 0.0
        private set

    private var touchdownStartTimeMs: Long = 0L
    private var lastEvidenceFailTimeMs: Long = 0L

    override fun reset() {
        currentState = ExerciseState.IDLE
        repCounter = 0
        validRepCounter = 0
        holdStartTimeMs = 0L
        currentHoldSeconds = 0.0
        maxHoldSecondsInRep = 0.0
        touchdownStartTimeMs = 0L
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
            } else if (timestampMs - lastEvidenceFailTimeMs > 1000L && currentState != ExerciseState.IDLE) {
                currentState = ExerciseState.IDLE
                holdStartTimeMs = 0L
                currentHoldSeconds = 0.0
            }
            return FsmUpdateResult(
                state = currentState,
                currentAngle = currentHoldSeconds,
                completedRep = null,
                totalReps = repCounter,
                validReps = validRepCounter,
                feedbackMessage = evidenceStatus.userMessage
            )
        }

        lastEvidenceFailTimeMs = 0L
        val elevation = smoothedMetric // Elevation percentage (0-100%)
        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        when (currentState) {
            ExerciseState.IDLE -> {
                currentHoldSeconds = 0.0
                feedbackMessage = "Stand near a sturdy support and lift one foot"
                if (elevation >= elevationThreshold) {
                    currentState = ExerciseState.START
                    holdStartTimeMs = timestampMs
                    touchdownStartTimeMs = 0L
                    feedbackMessage = "Hold initiated..."
                }
            }

            ExerciseState.START -> {
                currentState = ExerciseState.LOWERING // Active hold
                holdStartTimeMs = timestampMs
                touchdownStartTimeMs = 0L
                currentHoldSeconds = 0.0
                feedbackMessage = "Holding balance: 0.0s"
            }

            ExerciseState.LOWERING -> { // Active holding state
                val elapsedSec = (timestampMs - holdStartTimeMs) / 1000.0
                currentHoldSeconds = elapsedSec
                if (elapsedSec > maxHoldSecondsInRep) maxHoldSecondsInRep = elapsedSec

                feedbackMessage = String.format("Holding balance: %.1fs (target: ≥%.0fs)", elapsedSec, targetHoldSeconds)

                // Detect foot touchdown / loss of balance
                if (elevation < elevationThreshold * 0.7) {
                    if (touchdownStartTimeMs == 0L) {
                        touchdownStartTimeMs = timestampMs
                    } else if (timestampMs - touchdownStartTimeMs > 400L) {
                        // Confirmed touchdown
                        currentState = ExerciseState.COMPLETED
                    }
                } else {
                    touchdownStartTimeMs = 0L
                }

                // If completed
                if (currentState == ExerciseState.COMPLETED) {
                    val finalDurationSec = elapsedSec
                    val isValid = finalDurationSec >= targetHoldSeconds
                    val failureReasons = mutableListOf<String>()

                    if (!isValid) {
                        failureReasons.add(String.format("Hold duration was %.1fs, required ≥%.0fs", finalDurationSec, targetHoldSeconds))
                    }

                    repCounter++
                    if (isValid) {
                        validRepCounter++
                        feedbackMessage = String.format("Hold #%d successful! (%.1fs)", validRepCounter, finalDurationSec)
                    } else {
                        feedbackMessage = String.format("Balance held for %.1fs. Rest and try again.", finalDurationSec)
                    }

                    completedRep = RepRecord(
                        repNumber = repCounter,
                        isValid = isValid,
                        startTimestampMs = holdStartTimeMs,
                        peakTimestampMs = timestampMs,
                        endTimestampMs = timestampMs,
                        durationMs = (finalDurationSec * 1000).toLong(),
                        peakKneeAngle = finalDurationSec, // Stored as seconds hold
                        startKneeAngle = 0.0,
                        endKneeAngle = finalDurationSec,
                        feedbackMessage = feedbackMessage,
                        failureReasons = failureReasons
                    )

                    currentState = ExerciseState.START
                    holdStartTimeMs = 0L
                    currentHoldSeconds = 0.0
                    touchdownStartTimeMs = 0L

                    return FsmUpdateResult(
                        state = ExerciseState.COMPLETED,
                        currentAngle = finalDurationSec,
                        completedRep = completedRep,
                        totalReps = repCounter,
                        validReps = validRepCounter,
                        feedbackMessage = feedbackMessage
                    )
                }
            }

            ExerciseState.PEAK,
            ExerciseState.RISING -> {
                currentState = ExerciseState.LOWERING
            }

            ExerciseState.COMPLETED -> {
                currentState = ExerciseState.START
                feedbackMessage = "Ready for next balance hold."
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = currentHoldSeconds,
            completedRep = completedRep,
            totalReps = repCounter,
            validReps = validRepCounter,
            feedbackMessage = feedbackMessage
        )
    }
}
