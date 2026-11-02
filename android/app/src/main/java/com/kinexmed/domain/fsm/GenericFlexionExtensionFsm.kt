package com.kinexmed.domain.fsm

import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

enum class FlexionDirection {
    DECREASING, // Active phase decreases angle (Squat, Lunge, Bicep Curl, Marching)
    INCREASING  // Active phase increases angle (Calf Raise, Knee Extension, Shoulder Flexion/Abduction, Tricep)
}

data class FlexionExtensionConfig(
    val exerciseType: ExerciseType,
    val primaryMetricName: String,
    val primaryMetricUnit: String = "°",
    val direction: FlexionDirection,
    val startThreshold: Double,        // Threshold to initiate active rep
    val targetThreshold: Double,       // Prescribed target depth/extension
    val minValidThreshold: Double,     // Minimum acceptable angle to count as valid rep
    val returnThreshold: Double,       // Threshold confirming return to rest
    val minRepDurationMs: Long = 500L,
    val maxRepDurationMs: Long = 10000L,
    val targetDesc: String = "",
    val activePrompt: String = "Perform repetition",
    val returnPrompt: String = "Return to start position"
)

/**
 * High-performance, zero-latency deterministic state machine for flexion/extension movements.
 * Directly detects angular velocity inflection without artificial dwell buffers.
 */
class GenericFlexionExtensionFsm(
    val config: FlexionExtensionConfig
) : ExerciseStateMachine {

    override val exerciseType: ExerciseType = config.exerciseType
    override val primaryMetricName: String = config.primaryMetricName
    override val primaryMetricUnit: String = config.primaryMetricUnit
    override val targetThresholdDesc: String = if (config.targetDesc.isNotEmpty()) {
        config.targetDesc
    } else {
        val sym = if (config.direction == FlexionDirection.DECREASING) "≤" else "≥"
        "Target: $sym${config.targetThreshold.toInt()}${config.primaryMetricUnit}"
    }

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

    var extremeAngleInRep: Double = if (config.direction == FlexionDirection.DECREASING) 180.0 else 0.0
        private set

    var startAngleInRep: Double = 0.0
        private set

    private var prevAngle: Double = 0.0
    private var lastEvidenceFailTimeMs: Long = 0L

    override fun reset() {
        currentState = ExerciseState.IDLE
        repCounter = 0
        validRepCounter = 0
        repStartTimeMs = 0L
        peakTimeMs = 0L
        extremeAngleInRep = if (config.direction == FlexionDirection.DECREASING) 180.0 else 0.0
        startAngleInRep = 0.0
        prevAngle = 0.0
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
            } else if (timestampMs - lastEvidenceFailTimeMs > 1200L && currentState != ExerciseState.IDLE) {
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
        val currentAngle = smoothedMetric
        if (prevAngle == 0.0) prevAngle = currentAngle
        prevAngle = currentAngle

        var completedRep: RepRecord? = null
        var feedbackMessage = ""

        when (config.direction) {
            FlexionDirection.DECREASING -> {
                when (currentState) {
                    ExerciseState.IDLE -> {
                        feedbackMessage = "Position body to start"
                        if (currentAngle >= config.returnThreshold) {
                            currentState = ExerciseState.START
                            startAngleInRep = currentAngle
                            extremeAngleInRep = currentAngle
                            feedbackMessage = "Ready. Begin movement."
                        }
                    }

                    ExerciseState.START -> {
                        feedbackMessage = "Ready. Begin movement."
                        if (currentAngle > startAngleInRep) startAngleInRep = currentAngle
                        extremeAngleInRep = currentAngle

                        if (currentAngle <= config.startThreshold) {
                            currentState = ExerciseState.LOWERING
                            repStartTimeMs = timestampMs
                            extremeAngleInRep = currentAngle
                            feedbackMessage = config.activePrompt
                        }
                    }

                    ExerciseState.LOWERING -> {
                        feedbackMessage = config.activePrompt
                        if (currentAngle < extremeAngleInRep) {
                            extremeAngleInRep = currentAngle
                        }

                        // Inflection: user reached minimum and started extending upward by >= 3.5 deg
                        val hasInflected = currentAngle >= extremeAngleInRep + 3.5
                        val isDeepEnough = extremeAngleInRep <= config.minValidThreshold
                        if (hasInflected) {
                            currentState = ExerciseState.PEAK
                            peakTimeMs = timestampMs
                            feedbackMessage = if (isDeepEnough) "Good depth! Return." else "Return to start"
                        }
                    }

                    ExerciseState.PEAK -> {
                        feedbackMessage = config.returnPrompt
                        if (currentAngle < extremeAngleInRep) extremeAngleInRep = currentAngle

                        // Moving back towards start position
                        if (currentAngle >= extremeAngleInRep + 5.0 || currentAngle >= config.startThreshold) {
                            currentState = ExerciseState.RISING
                            feedbackMessage = "Returning to start..."
                        }
                    }

                    ExerciseState.RISING -> {
                        feedbackMessage = "Return all the way to complete rep"

                        // Rep completion when angle crosses return threshold
                        if (currentAngle >= config.returnThreshold) {
                            currentState = ExerciseState.COMPLETED
                            val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                            val failureReasons = mutableListOf<String>()
                            var isValid = true

                            if (extremeAngleInRep > config.minValidThreshold) {
                                isValid = false
                                failureReasons.add("Insufficient range: reached ${extremeAngleInRep.toInt()}°, target was ≤${config.minValidThreshold.toInt()}°")
                            }

                            if (durationMs < config.minRepDurationMs) {
                                isValid = false
                                failureReasons.add("Too fast (${durationMs}ms), maintain steady control")
                            } else if (durationMs > config.maxRepDurationMs) {
                                isValid = false
                                failureReasons.add("Rep duration exceeded limit")
                            }

                            repCounter++
                            if (isValid) {
                                validRepCounter++
                                feedbackMessage = "Rep #$validRepCounter complete! Good control."
                            } else {
                                feedbackMessage = if (extremeAngleInRep > config.minValidThreshold) {
                                    "Increase range of motion next rep"
                                } else {
                                    "Maintain steady pace"
                                }
                            }

                            completedRep = RepRecord(
                                repNumber = repCounter,
                                isValid = isValid,
                                startTimestampMs = repStartTimeMs,
                                peakTimestampMs = if (peakTimeMs > 0L) peakTimeMs else timestampMs,
                                endTimestampMs = timestampMs,
                                durationMs = durationMs,
                                peakKneeAngle = extremeAngleInRep,
                                startKneeAngle = startAngleInRep,
                                endKneeAngle = currentAngle,
                                feedbackMessage = feedbackMessage,
                                failureReasons = failureReasons
                            )

                            currentState = ExerciseState.START
                            startAngleInRep = currentAngle
                            extremeAngleInRep = currentAngle
                            repStartTimeMs = 0L
                            peakTimeMs = 0L

                            return FsmUpdateResult(
                                state = ExerciseState.COMPLETED,
                                currentAngle = currentAngle,
                                completedRep = completedRep,
                                totalReps = repCounter,
                                validReps = validRepCounter,
                                feedbackMessage = feedbackMessage
                            )
                        }
                    }

                    ExerciseState.COMPLETED -> {
                        currentState = ExerciseState.START
                        feedbackMessage = "Ready. Begin next rep."
                    }
                }
            }

            FlexionDirection.INCREASING -> {
                when (currentState) {
                    ExerciseState.IDLE -> {
                        feedbackMessage = "Position body to start"
                        if (currentAngle <= config.returnThreshold) {
                            currentState = ExerciseState.START
                            startAngleInRep = currentAngle
                            extremeAngleInRep = currentAngle
                            feedbackMessage = "Ready. Begin movement."
                        }
                    }

                    ExerciseState.START -> {
                        feedbackMessage = "Ready. Begin movement."
                        if (currentAngle < startAngleInRep) startAngleInRep = currentAngle
                        extremeAngleInRep = currentAngle

                        if (currentAngle >= config.startThreshold) {
                            currentState = ExerciseState.LOWERING // Active extension
                            repStartTimeMs = timestampMs
                            extremeAngleInRep = currentAngle
                            feedbackMessage = config.activePrompt
                        }
                    }

                    ExerciseState.LOWERING -> {
                        feedbackMessage = config.activePrompt
                        if (currentAngle > extremeAngleInRep) {
                            extremeAngleInRep = currentAngle
                        }

                        // Inflection: reached peak angle and reversed downward by >= 3.5 deg
                        val hasInflected = currentAngle <= extremeAngleInRep - 3.5
                        val isHighEnough = extremeAngleInRep >= config.minValidThreshold
                        if (hasInflected) {
                            currentState = ExerciseState.PEAK
                            peakTimeMs = timestampMs
                            feedbackMessage = if (isHighEnough) "Good extension! Lower slowly." else "Lower slowly"
                        }
                    }

                    ExerciseState.PEAK -> {
                        feedbackMessage = config.returnPrompt
                        if (currentAngle > extremeAngleInRep) extremeAngleInRep = currentAngle

                        if (currentAngle <= extremeAngleInRep - 5.0 || currentAngle <= config.startThreshold) {
                            currentState = ExerciseState.RISING
                            feedbackMessage = "Returning to start..."
                        }
                    }

                    ExerciseState.RISING -> {
                        feedbackMessage = "Return all the way to complete rep"

                        if (currentAngle <= config.returnThreshold) {
                            currentState = ExerciseState.COMPLETED
                            val durationMs = (timestampMs - repStartTimeMs).coerceAtLeast(0L)
                            val failureReasons = mutableListOf<String>()
                            var isValid = true

                            if (extremeAngleInRep < config.minValidThreshold) {
                                isValid = false
                                failureReasons.add("Insufficient range: reached ${extremeAngleInRep.toInt()}°, target was ≥${config.minValidThreshold.toInt()}°")
                            }

                            if (durationMs < config.minRepDurationMs) {
                                isValid = false
                                failureReasons.add("Too fast (${durationMs}ms), maintain control")
                            } else if (durationMs > config.maxRepDurationMs) {
                                isValid = false
                                failureReasons.add("Rep duration exceeded limit")
                            }

                            repCounter++
                            if (isValid) {
                                validRepCounter++
                                feedbackMessage = "Rep #$validRepCounter complete! Great form."
                            } else {
                                feedbackMessage = if (extremeAngleInRep < config.minValidThreshold) {
                                    "Aim for full extension next rep"
                                } else {
                                    "Maintain steady pace"
                                }
                            }

                            completedRep = RepRecord(
                                repNumber = repCounter,
                                isValid = isValid,
                                startTimestampMs = repStartTimeMs,
                                peakTimestampMs = if (peakTimeMs > 0L) peakTimeMs else timestampMs,
                                endTimestampMs = timestampMs,
                                durationMs = durationMs,
                                peakKneeAngle = extremeAngleInRep,
                                startKneeAngle = startAngleInRep,
                                endKneeAngle = currentAngle,
                                feedbackMessage = feedbackMessage,
                                failureReasons = failureReasons
                            )

                            currentState = ExerciseState.START
                            startAngleInRep = currentAngle
                            extremeAngleInRep = currentAngle
                            repStartTimeMs = 0L
                            peakTimeMs = 0L

                            return FsmUpdateResult(
                                state = ExerciseState.COMPLETED,
                                currentAngle = currentAngle,
                                completedRep = completedRep,
                                totalReps = repCounter,
                                validReps = validRepCounter,
                                feedbackMessage = feedbackMessage
                            )
                        }
                    }

                    ExerciseState.COMPLETED -> {
                        currentState = ExerciseState.START
                        feedbackMessage = "Ready. Begin next rep."
                    }
                }
            }
        }

        return FsmUpdateResult(
            state = currentState,
            currentAngle = currentAngle,
            completedRep = completedRep,
            totalReps = repCounter,
            validReps = validRepCounter,
            feedbackMessage = feedbackMessage
        )
    }
}
