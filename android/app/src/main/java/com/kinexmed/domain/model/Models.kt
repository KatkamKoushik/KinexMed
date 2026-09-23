package com.kinexmed.domain.model

/**
 * 3D Normalized landmark from MediaPipe Pose Landmarker.
 * x and y are normalized to [0.0, 1.0] by the image width and height.
 * z represents landmark depth roughly in the same scale as x.
 * visibility and presence represent confidence in [0.0, 1.0].
 */
data class Landmark(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float = 1.0f,
    val presence: Float = 1.0f
) {
    fun isConfidenceAbove(threshold: Float): Boolean {
        return ((visibility + presence) / 2f) >= threshold
    }

    fun isInsideFrame(margin: Float = 0.02f): Boolean {
        return x in margin..(1.0f - margin) && y in margin..(1.0f - margin)
    }
}

/**
 * Aggregated evidence failure episode to prevent frame-by-frame warning explosion.
 */
data class EvidenceEpisode(
    val reason: EvidenceFailureReason,
    val startTimeMs: Long,
    var endTimeMs: Long,
    var frameCount: Int = 1
) {
    val durationMs: Long get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)
}

/**
 * Common MediaPipe Pose landmark indices.
 */
object PoseLandmarks {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32
}

/**
 * Result of joint angle calculation.
 */
data class JointAngle(
    val jointName: String,
    val angleDegrees: Double,
    val confidence: Float,
    val timestampMs: Long
)

/**
 * Exercise state machine states.
 */
enum class ExerciseState {
    IDLE,
    START,
    LOWERING,
    PEAK,
    RISING,
    COMPLETED
}

/**
 * Evidence engine status and guidance.
 */
sealed class EvidenceStatus {
    object Sufficient : EvidenceStatus()
    data class Insufficient(val reason: EvidenceFailureReason, val userMessage: String) : EvidenceStatus()
}

enum class EvidenceFailureReason {
    LANDMARKS_MISSING,
    LOW_CONFIDENCE,
    OUT_OF_FRAME,
    BODY_OCCLUSION_OR_TOO_CLOSE,
    UNSTABLE_TRACKING
}

/**
 * Completed repetition record.
 */
data class RepRecord(
    val repNumber: Int,
    val isValid: Boolean,
    val startTimestampMs: Long,
    val peakTimestampMs: Long,
    val endTimestampMs: Long,
    val durationMs: Long,
    val peakKneeAngle: Double,
    val startKneeAngle: Double,
    val endKneeAngle: Double,
    val feedbackMessage: String,
    val failureReasons: List<String> = emptyList(),
    val evidenceImagePath: String? = null,
    val videoTimestampMs: Long? = null,
    val targetAngle: Double = 0.0
)

enum class ExerciseCategory(val displayName: String) {
    LOWER_BODY("Lower Body"),
    UPPER_BODY("Upper Body"),
    FUNCTIONAL_MOBILITY("Functional & Mobility"),
    BALANCE("Balance & Stability")
}

enum class ExerciseType(val id: String, val displayName: String, val category: ExerciseCategory) {
    SQUAT("squat", "Bilateral Squat", ExerciseCategory.LOWER_BODY),
    SIT_TO_STAND("sit_to_stand", "Sit-to-Stand", ExerciseCategory.LOWER_BODY),
    FORWARD_LUNGE("forward_lunge", "Forward Lunge", ExerciseCategory.LOWER_BODY),
    REVERSE_LUNGE("reverse_lunge", "Reverse Lunge", ExerciseCategory.LOWER_BODY),
    CALF_RAISE("calf_raise", "Calf Raise", ExerciseCategory.LOWER_BODY),
    KNEE_EXTENSION("knee_extension", "Seated Knee Extension", ExerciseCategory.LOWER_BODY),
    HIP_ABDUCTION("hip_abduction", "Standing Hip Abduction", ExerciseCategory.LOWER_BODY),
    HIP_EXTENSION("hip_extension", "Standing Hip Extension", ExerciseCategory.LOWER_BODY),
    MARCHING_IN_PLACE("marching_in_place", "Marching in Place", ExerciseCategory.FUNCTIONAL_MOBILITY),
    SHOULDER_FLEXION("shoulder_flexion", "Shoulder Flexion", ExerciseCategory.UPPER_BODY),
    SHOULDER_ABDUCTION("shoulder_abduction", "Shoulder Abduction", ExerciseCategory.UPPER_BODY),
    ELBOW_FLEXION("elbow_flexion", "Elbow Flexion (Bicep Curl)", ExerciseCategory.UPPER_BODY),
    ELBOW_EXTENSION("elbow_extension", "Elbow Extension (Tricep)", ExerciseCategory.UPPER_BODY),
    HEEL_TOE_RAISE("heel_toe_raise", "Heel-to-Toe Rocking", ExerciseCategory.FUNCTIONAL_MOBILITY),
    SINGLE_LEG_BALANCE("single_leg_balance", "Supported Single-Leg Balance", ExerciseCategory.BALANCE);

    companion object {
        fun fromId(id: String): ExerciseType {
            return entries.find { it.id.equals(id, ignoreCase = true) }
                ?: if (id.equals("lunge", ignoreCase = true)) FORWARD_LUNGE else SQUAT
        }
    }
}

enum class ExerciseValidationStatus(val label: String) {
    PHYSICALLY_DEMONSTRATED("Physically Demonstrated"),
    IMPLEMENTED_MODULE("Implemented Module"),
    PLANNED("Planned")
}

data class ExerciseDefinition(
    val type: ExerciseType,
    val displayName: String,
    val category: ExerciseCategory,
    val status: ExerciseValidationStatus,
    val shortDescription: String,
    val instructions: List<String>,
    val cameraDistance: String,
    val cameraPlacement: String,
    val requiredVisibility: String,
    val targetMetricName: String,
    val targetMetricTarget: String,
    val primaryJointName: String
)

/**
 * Exercise session summary.
 */
data class SessionSummary(
    val sessionId: String,
    val exerciseName: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationSeconds: Double,
    val totalReps: Int,
    val validReps: Int,
    val avgPeakKneeAngle: Double,
    val minKneeAngle: Double,
    val maxKneeAngle: Double,
    val evidenceFailureCount: Int,
    val reps: List<RepRecord>,
    val evidenceEpisodes: List<EvidenceEpisode> = emptyList(),
    val videoRecordingPath: String? = null,
    val isRecordingEnabled: Boolean = false
)
