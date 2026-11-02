package com.kinexmed.domain.evidence

import android.util.Log
import com.kinexmed.domain.model.EvidenceFailureReason
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.PoseLandmarks
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Configuration parameters for visual evidence validation.
 * Calibrated for wide-angle mobile cameras placed at 2.0 - 3.0 meters.
 */
data class EvidenceConfig(
    val minLandmarkConfidence: Float = 0.45f,
    val frameBoundaryMargin: Float = 0.015f,
    val minVerticalBodySpan: Float = 0.22f,
    val maxVerticalBodySpan: Float = 0.97f,
    val maxJointVelocityNormalizedPerSec: Double = 4.0,
    val maxTransientFailureFrames: Int = 3
)

/**
 * Validates visual evidence BEFORE feeding frames to the geometry engine,
 * state machine, or rule evaluator.
 *
 * Implements sensible temporal tolerance:
 * 1-3 transient bad frames do not abort tracking or flash guidance.
 * Sustained poor evidence halts exercise counting and provides actionable feedback.
 */
class EvidenceEngine(
    val config: EvidenceConfig = EvidenceConfig()
) {
    companion object {
        private const val TAG = "EvidenceEngine"
    }

    private var lastValidKeypointPos: Pair<Float, Float>? = null
    private var lastTimestampMs: Long = 0L
    private var activeSide: Int? = null // 0 for Left, 1 for Right (hysteresis)

    // Temporal tolerance debounce state
    private var consecutiveFailureFrames = 0
    private var lastFailureStatus: EvidenceStatus.Insufficient? = null

    /**
     * Evaluates visual evidence for a specific rehabilitation exercise.
     * Checks presence, confidence, boundary margins, full-body visibility, and tracking stability.
     */
    fun evaluateEvidence(
        landmarks: Map<Int, Landmark>,
        timestampMs: Long,
        exerciseType: com.kinexmed.domain.model.ExerciseType = com.kinexmed.domain.model.ExerciseType.SQUAT
    ): EvidenceStatus {
        val rawStatus = evaluateRawFrame(landmarks, timestampMs)

        if (rawStatus is EvidenceStatus.Insufficient) {
            consecutiveFailureFrames++
            lastFailureStatus = rawStatus

            Log.d(TAG, "Frame evidence failure #$consecutiveFailureFrames ($exerciseType): ${rawStatus.reason} - ${rawStatus.userMessage}")

            // If we previously had good tracking and failure is transient (<= 3 frames), maintain continuity
            if (consecutiveFailureFrames <= config.maxTransientFailureFrames && lastValidKeypointPos != null) {
                return EvidenceStatus.Sufficient
            }

            return rawStatus
        }

        // Frame passed all checks: reset failure streak
        consecutiveFailureFrames = 0
        lastFailureStatus = null
        return EvidenceStatus.Sufficient
    }

    /**
     * Backwards-compatible squat evidence evaluator.
     */
    fun evaluateSquatEvidence(
        landmarks: Map<Int, Landmark>,
        timestampMs: Long
    ): EvidenceStatus {
        return evaluateEvidence(landmarks, timestampMs, com.kinexmed.domain.model.ExerciseType.SQUAT)
    }

    private fun evaluateRawFrame(
        landmarks: Map<Int, Landmark>,
        timestampMs: Long
    ): EvidenceStatus {
        // 1. Check if landmarks collection is populated
        if (landmarks.isEmpty()) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.LANDMARKS_MISSING,
                "No person detected. Please step into the camera view."
            )
        }

        // Check head/torso presence (Nose or Shoulders)
        val hasUpperBody = (landmarks[PoseLandmarks.NOSE]?.isConfidenceAbove(config.minLandmarkConfidence) == true) ||
                (landmarks[PoseLandmarks.LEFT_SHOULDER]?.isConfidenceAbove(config.minLandmarkConfidence) == true) ||
                (landmarks[PoseLandmarks.RIGHT_SHOULDER]?.isConfidenceAbove(config.minLandmarkConfidence) == true)

        if (!hasUpperBody) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.LANDMARKS_MISSING,
                "Upper body not clearly visible. Please adjust camera height."
            )
        }

        // Check required lower body joints: at least one complete side (Left or Right: Hip + Knee + Ankle)
        val leftHip = landmarks[PoseLandmarks.LEFT_HIP]
        val leftKnee = landmarks[PoseLandmarks.LEFT_KNEE]
        val leftAnkle = landmarks[PoseLandmarks.LEFT_ANKLE]

        val rightHip = landmarks[PoseLandmarks.RIGHT_HIP]
        val rightKnee = landmarks[PoseLandmarks.RIGHT_KNEE]
        val rightAnkle = landmarks[PoseLandmarks.RIGHT_ANKLE]

        val leftSideComplete = leftHip != null && leftKnee != null && leftAnkle != null
        val rightSideComplete = rightHip != null && rightKnee != null && rightAnkle != null

        if (!leftSideComplete && !rightSideComplete) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.LANDMARKS_MISSING,
                "Move slightly back so I can see your full body."
            )
        }

        // Determine primary tracking side with hysteresis to prevent frame-to-frame flip-flopping
        val (activeHip, activeKnee, activeAnkle) = when {
            leftSideComplete && rightSideComplete -> {
                val leftAvgConf = (leftHip!!.visibility + leftKnee!!.visibility + leftAnkle!!.visibility) / 3f
                val rightAvgConf = (rightHip!!.visibility + rightKnee!!.visibility + rightAnkle!!.visibility) / 3f

                val chosen = if (activeSide == 0 && (leftAvgConf + 0.05f >= rightAvgConf)) {
                    0
                } else if (activeSide == 1 && (rightAvgConf + 0.05f >= leftAvgConf)) {
                    1
                } else if (leftAvgConf >= rightAvgConf) {
                    0
                } else {
                    1
                }
                activeSide = chosen
                if (chosen == 0) Triple(leftHip, leftKnee, leftAnkle) else Triple(rightHip, rightKnee, rightAnkle)
            }
            leftSideComplete -> {
                activeSide = 0
                Triple(leftHip!!, leftKnee!!, leftAnkle!!)
            }
            else -> {
                activeSide = 1
                Triple(rightHip!!, rightKnee!!, rightAnkle!!)
            }
        }

        // 2. Landmark Confidence Check
        if (!activeHip.isConfidenceAbove(config.minLandmarkConfidence) ||
            !activeKnee.isConfidenceAbove(config.minLandmarkConfidence) ||
            !activeAnkle.isConfidenceAbove(config.minLandmarkConfidence)
        ) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.LOW_CONFIDENCE,
                "Move slightly back so I can see your full body."
            )
        }

        // 3. Usable Frame Boundary Check
        val joints = listOf(activeHip, activeKnee, activeAnkle)
        for (joint in joints) {
            if (!joint.isInsideFrame(config.frameBoundaryMargin)) {
                return EvidenceStatus.Insufficient(
                    EvidenceFailureReason.OUT_OF_FRAME,
                    if (joint.y > 1.0f - config.frameBoundaryMargin) {
                        "Move slightly back so your feet are in view."
                    } else if (joint.y < config.frameBoundaryMargin) {
                        "Tilt camera down or step back slightly."
                    } else {
                        "Move towards the center of the camera frame."
                    }
                )
            }
        }

        // 4. Body Visibility & Proximity Span
        val topY = landmarks[PoseLandmarks.NOSE]?.y
            ?: minOf(landmarks[PoseLandmarks.LEFT_SHOULDER]?.y ?: 0.2f, landmarks[PoseLandmarks.RIGHT_SHOULDER]?.y ?: 0.2f)
        val bottomY = maxOf(activeAnkle.y, activeKnee.y)
        val verticalSpan = abs(bottomY - topY)

        if (verticalSpan < config.minVerticalBodySpan) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.BODY_OCCLUSION_OR_TOO_CLOSE,
                "You are too far from the camera. Please move closer."
            )
        }

        if (verticalSpan > config.maxVerticalBodySpan) {
            return EvidenceStatus.Insufficient(
                EvidenceFailureReason.BODY_OCCLUSION_OR_TOO_CLOSE,
                "Move slightly back so I can see your full body."
            )
        }

        // 5. Tracking Stability Check (prevents sudden tracker identity swapping)
        if (lastValidKeypointPos != null && lastTimestampMs > 0L) {
            val dtSec = (timestampMs - lastTimestampMs) / 1000.0
            if (dtSec in 0.005..0.5) {
                val dx = activeKnee.x - lastValidKeypointPos!!.first
                val dy = activeKnee.y - lastValidKeypointPos!!.second
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                val velocity = dist / dtSec

                if (velocity > config.maxJointVelocityNormalizedPerSec) {
                    lastTimestampMs = timestampMs
                    return EvidenceStatus.Insufficient(
                        EvidenceFailureReason.UNSTABLE_TRACKING,
                        "Tracking unstable. Please hold your position for a moment."
                    )
                }
            }
        }

        // Record successful keypoint position and timestamp
        lastValidKeypointPos = Pair(activeKnee.x, activeKnee.y)
        lastTimestampMs = timestampMs

        return EvidenceStatus.Sufficient
    }

    /**
     * Resets tracking memory and debouncing state when starting a new session or re-calibrating.
     */
    fun reset() {
        lastValidKeypointPos = null
        lastTimestampMs = 0L
        activeSide = null
        consecutiveFailureFrames = 0
        lastFailureStatus = null
    }
}
