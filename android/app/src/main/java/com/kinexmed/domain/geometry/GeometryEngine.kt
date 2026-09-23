package com.kinexmed.domain.geometry

import com.kinexmed.domain.model.JointAngle
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.PoseLandmarks
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Deterministic mathematical geometry engine for joint kinematics.
 * Calculates biomechanically accurate interior joint angles without approximations.
 */
object GeometryEngine {

    private const val RAD_TO_DEG = 180.0 / Math.PI
    private const val EPSILON = 1e-6
    private const val SIDE_HYSTERESIS_THRESHOLD = 0.08f

    @Volatile
    private var lastPreferredKneeSide: String? = null
    @Volatile
    private var lastPreferredHipSide: String? = null
    @Volatile
    private var lastPreferredElbowSide: String? = null
    @Volatile
    private var lastPreferredShoulderSide: String? = null

    /**
     * Resets side-preference hysteresis. Call at session start.
     */
    fun resetSidePreferences() {
        lastPreferredKneeSide = null
        lastPreferredHipSide = null
        lastPreferredElbowSide = null
        lastPreferredShoulderSide = null
    }

    /**
     * Calculates the 2D interior angle in degrees at vertex [b], formed by ray b->a and ray b->c.
     * Angle range is [0.0, 180.0].
     * An angle of ~180° indicates fully extended joint (e.g. standing erect).
     * An angle of ~90° indicates 90° flexion (e.g. parallel squat).
     */
    fun calculateAngle2D(
        a: Landmark,
        b: Landmark,
        c: Landmark,
        timestampMs: Long = 0L,
        jointName: String = "joint"
    ): JointAngle {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()

        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()

        val dot = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)

        if (mag1 < EPSILON || mag2 < EPSILON) {
            val meanConf = (a.visibility + b.visibility + c.visibility) / 3.0f
            return JointAngle(jointName, 180.0, meanConf, timestampMs)
        }

        var cosTheta = dot / (mag1 * mag2)
        // Clamp to avoid numerical errors with acos
        if (cosTheta > 1.0) cosTheta = 1.0
        if (cosTheta < -1.0) cosTheta = -1.0

        val angleDegrees = acos(cosTheta) * RAD_TO_DEG
        val confidence = minOf(a.visibility, minOf(b.visibility, c.visibility))

        return JointAngle(
            jointName = jointName,
            angleDegrees = angleDegrees,
            confidence = confidence,
            timestampMs = timestampMs
        )
    }

    /**
     * Calculates the 3D interior angle in degrees at vertex [b], taking into account depth z.
     */
    fun calculateAngle3D(
        a: Landmark,
        b: Landmark,
        c: Landmark,
        timestampMs: Long = 0L,
        jointName: String = "joint"
    ): JointAngle {
        val v1x = (a.x - b.x).toDouble()
        val v1y = (a.y - b.y).toDouble()
        val v1z = (a.z - b.z).toDouble()

        val v2x = (c.x - b.x).toDouble()
        val v2y = (c.y - b.y).toDouble()
        val v2z = (c.z - b.z).toDouble()

        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)

        if (mag1 < EPSILON || mag2 < EPSILON) {
            val meanConf = (a.visibility + b.visibility + c.visibility) / 3.0f
            return JointAngle(jointName, 180.0, meanConf, timestampMs)
        }

        var cosTheta = dot / (mag1 * mag2)
        if (cosTheta > 1.0) cosTheta = 1.0
        if (cosTheta < -1.0) cosTheta = -1.0

        val angleDegrees = acos(cosTheta) * RAD_TO_DEG
        val confidence = minOf(a.visibility, minOf(b.visibility, c.visibility))

        return JointAngle(
            jointName = jointName,
            angleDegrees = angleDegrees,
            confidence = confidence,
            timestampMs = timestampMs
        )
    }

    // ==========================================
    // LOWER BODY JOINTS (KNEE & HIP)
    // ==========================================

    /**
     * Extracts Left Knee Angle (Hip 23 -> Knee 25 -> Ankle 27).
     */
    fun calculateLeftKneeAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val hip = landmarks[PoseLandmarks.LEFT_HIP] ?: return null
        val knee = landmarks[PoseLandmarks.LEFT_KNEE] ?: return null
        val ankle = landmarks[PoseLandmarks.LEFT_ANKLE] ?: return null
        return calculateAngle2D(hip, knee, ankle, timestampMs, "left_knee")
    }

    /**
     * Extracts Right Knee Angle (Hip 24 -> Knee 26 -> Ankle 28).
     */
    fun calculateRightKneeAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val hip = landmarks[PoseLandmarks.RIGHT_HIP] ?: return null
        val knee = landmarks[PoseLandmarks.RIGHT_KNEE] ?: return null
        val ankle = landmarks[PoseLandmarks.RIGHT_ANKLE] ?: return null
        return calculateAngle2D(hip, knee, ankle, timestampMs, "right_knee")
    }

    /**
     * Returns the knee angle for the side with higher tracking confidence.
     * Applies hysteresis (+-0.08 margin) to prevent frame-to-frame switching jitter.
     */
    fun getPrimaryKneeAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftKneeAngle(landmarks, timestampMs)
        val right = calculateRightKneeAngle(landmarks, timestampMs)

        val selected = when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                val lConf = left!!.confidence
                val rConf = right!!.confidence
                when (lastPreferredKneeSide) {
                    "left" -> if (rConf > lConf + SIDE_HYSTERESIS_THRESHOLD) right else left
                    "right" -> if (lConf > rConf + SIDE_HYSTERESIS_THRESHOLD) left else right
                    else -> if (lConf >= rConf) left else right
                }
            }
        }
        if (selected != null) {
            lastPreferredKneeSide = if (selected.jointName.startsWith("left")) "left" else "right"
        }
        return selected
    }

    /**
     * Extracts Left Hip Angle (Shoulder 11 -> Hip 23 -> Knee 25).
     * ~180° indicates upright standing.
     * ~90° indicates 90° hip flexion (e.g. sitting or deep squat).
     */
    fun calculateLeftHipAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val shoulder = landmarks[PoseLandmarks.LEFT_SHOULDER] ?: return null
        val hip = landmarks[PoseLandmarks.LEFT_HIP] ?: return null
        val knee = landmarks[PoseLandmarks.LEFT_KNEE] ?: return null
        return calculateAngle2D(shoulder, hip, knee, timestampMs, "left_hip")
    }

    /**
     * Extracts Right Hip Angle (Shoulder 12 -> Hip 24 -> Knee 26).
     */
    fun calculateRightHipAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val shoulder = landmarks[PoseLandmarks.RIGHT_SHOULDER] ?: return null
        val hip = landmarks[PoseLandmarks.RIGHT_HIP] ?: return null
        val knee = landmarks[PoseLandmarks.RIGHT_KNEE] ?: return null
        return calculateAngle2D(shoulder, hip, knee, timestampMs, "right_hip")
    }

    /**
     * Returns the primary hip angle with hysteresis stabilization.
     */
    fun getPrimaryHipAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftHipAngle(landmarks, timestampMs)
        val right = calculateRightHipAngle(landmarks, timestampMs)

        val selected = when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                val lConf = left!!.confidence
                val rConf = right!!.confidence
                when (lastPreferredHipSide) {
                    "left" -> if (rConf > lConf + SIDE_HYSTERESIS_THRESHOLD) right else left
                    "right" -> if (lConf > rConf + SIDE_HYSTERESIS_THRESHOLD) left else right
                    else -> if (lConf >= rConf) left else right
                }
            }
        }
        if (selected != null) {
            lastPreferredHipSide = if (selected.jointName.startsWith("left")) "left" else "right"
        }
        return selected
    }

    /**
     * Extracts Left Ankle Angle (Knee 25 -> Ankle 27 -> Foot Index 31).
     * ~90° indicates neutral standing flat on ground.
     * >120° indicates plantarflexion (calf raise heel lift).
     */
    fun calculateLeftAnkleAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val knee = landmarks[PoseLandmarks.LEFT_KNEE] ?: return null
        val ankle = landmarks[PoseLandmarks.LEFT_ANKLE] ?: return null
        val foot = landmarks[PoseLandmarks.LEFT_FOOT_INDEX] ?: return null
        return calculateAngle2D(knee, ankle, foot, timestampMs, "left_ankle")
    }

    /**
     * Extracts Right Ankle Angle (Knee 26 -> Ankle 28 -> Foot Index 32).
     */
    fun calculateRightAnkleAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val knee = landmarks[PoseLandmarks.RIGHT_KNEE] ?: return null
        val ankle = landmarks[PoseLandmarks.RIGHT_ANKLE] ?: return null
        val foot = landmarks[PoseLandmarks.RIGHT_FOOT_INDEX] ?: return null
        return calculateAngle2D(knee, ankle, foot, timestampMs, "right_ankle")
    }

    fun getPrimaryAnkleAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftAnkleAngle(landmarks, timestampMs)
        val right = calculateRightAnkleAngle(landmarks, timestampMs)

        return when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                if (left!!.confidence >= right!!.confidence) left else right
            }
        }
    }

    /**
     * For lunges: detects the lead knee flexion (the knee undergoing deeper flexion).
     */
    fun getLeadKneeAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftKneeAngle(landmarks, timestampMs)
        val right = calculateRightKneeAngle(landmarks, timestampMs)

        return when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                // Return whichever leg has lower angle (more deeply flexed)
                if (left!!.angleDegrees <= right!!.angleDegrees) left else right
            }
        }
    }

    // ==========================================
    // UPPER BODY JOINTS (ELBOW & SHOULDER)
    // ==========================================

    /**
     * Extracts Left Elbow Angle (Shoulder 11 -> Elbow 13 -> Wrist 15).
     * ~180° indicates fully extended arm.
     * ~45°-60° indicates peak bicep flexion.
     */
    fun calculateLeftElbowAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val shoulder = landmarks[PoseLandmarks.LEFT_SHOULDER] ?: return null
        val elbow = landmarks[PoseLandmarks.LEFT_ELBOW] ?: return null
        val wrist = landmarks[PoseLandmarks.LEFT_WRIST] ?: return null
        return calculateAngle2D(shoulder, elbow, wrist, timestampMs, "left_elbow")
    }

    /**
     * Extracts Right Elbow Angle (Shoulder 12 -> Elbow 14 -> Wrist 16).
     */
    fun calculateRightElbowAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val shoulder = landmarks[PoseLandmarks.RIGHT_SHOULDER] ?: return null
        val elbow = landmarks[PoseLandmarks.RIGHT_ELBOW] ?: return null
        val wrist = landmarks[PoseLandmarks.RIGHT_WRIST] ?: return null
        return calculateAngle2D(shoulder, elbow, wrist, timestampMs, "right_elbow")
    }

    /**
     * Returns primary elbow angle with side-preference hysteresis.
     */
    fun getPrimaryElbowAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftElbowAngle(landmarks, timestampMs)
        val right = calculateRightElbowAngle(landmarks, timestampMs)

        val selected = when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                val lConf = left!!.confidence
                val rConf = right!!.confidence
                when (lastPreferredElbowSide) {
                    "left" -> if (rConf > lConf + SIDE_HYSTERESIS_THRESHOLD) right else left
                    "right" -> if (lConf > rConf + SIDE_HYSTERESIS_THRESHOLD) left else right
                    else -> if (lConf >= rConf) left else right
                }
            }
        }
        if (selected != null) {
            lastPreferredElbowSide = if (selected.jointName.startsWith("left")) "left" else "right"
        }
        return selected
    }

    /**
     * Left Shoulder Flexion Angle (Hip 23 -> Shoulder 11 -> Elbow 13).
     * Arm at side: ~10°-20°.
     * Arm forward horizontal: ~90°.
     * Arm overhead: ~160°-180°.
     */
    fun calculateLeftShoulderAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val hip = landmarks[PoseLandmarks.LEFT_HIP] ?: return null
        val shoulder = landmarks[PoseLandmarks.LEFT_SHOULDER] ?: return null
        val elbow = landmarks[PoseLandmarks.LEFT_ELBOW] ?: return null
        return calculateAngle2D(hip, shoulder, elbow, timestampMs, "left_shoulder")
    }

    /**
     * Right Shoulder Flexion Angle (Hip 24 -> Shoulder 12 -> Elbow 14).
     */
    fun calculateRightShoulderAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val hip = landmarks[PoseLandmarks.RIGHT_HIP] ?: return null
        val shoulder = landmarks[PoseLandmarks.RIGHT_SHOULDER] ?: return null
        val elbow = landmarks[PoseLandmarks.RIGHT_ELBOW] ?: return null
        return calculateAngle2D(hip, shoulder, elbow, timestampMs, "right_shoulder")
    }

    /**
     * Returns primary shoulder angle with hysteresis.
     */
    fun getPrimaryShoulderAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftShoulderAngle(landmarks, timestampMs)
        val right = calculateRightShoulderAngle(landmarks, timestampMs)

        val selected = when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                val lConf = left!!.confidence
                val rConf = right!!.confidence
                when (lastPreferredShoulderSide) {
                    "left" -> if (rConf > lConf + SIDE_HYSTERESIS_THRESHOLD) right else left
                    "right" -> if (lConf > rConf + SIDE_HYSTERESIS_THRESHOLD) left else right
                    else -> if (lConf >= rConf) left else right
                }
            }
        }
        if (selected != null) {
            lastPreferredShoulderSide = if (selected.jointName.startsWith("left")) "left" else "right"
        }
        return selected
    }

    // ==========================================
    // FUNCTIONAL, MOBILITY & BALANCE METRICS
    // ==========================================

    /**
     * Seated Knee Extension:
     * Patient is seated (Hip flexed ~90°).
     * Knee angle starts flexed at ~90°.
     * Leg kicks out straight to ~160°-180°.
     */
    fun getSeatedKneeExtensionAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        return getPrimaryKneeAngle(landmarks, timestampMs)
    }

    /**
     * Standing Hip Abduction:
     * Measures angle of thigh moving laterally away from midline.
     * In standing, shoulder-hip-knee is ~180°. During abduction, angle shifts ~30°-45°.
     */
    fun getStandingHipAbductionAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        return getPrimaryHipAngle(landmarks, timestampMs)
    }

    /**
     * Standing Hip Extension:
     * Measures leg extending backward past midline.
     */
    fun getStandingHipExtensionAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        return getPrimaryHipAngle(landmarks, timestampMs)
    }

    /**
     * Marching in Place (High Knees):
     * Tracks the hip of the leg being lifted (lower angle = higher knee raise).
     */
    fun getMarchingHipFlexionAngle(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val left = calculateLeftHipAngle(landmarks, timestampMs)
        val right = calculateRightHipAngle(landmarks, timestampMs)

        return when {
            left == null && right == null -> null
            left != null && right == null -> left
            left == null && right != null -> right
            else -> {
                // Return whichever hip is more flexed (lower interior angle)
                if (left!!.angleDegrees <= right!!.angleDegrees) left else right
            }
        }
    }

    /**
     * Supported Single-Leg Balance:
     * Computes the vertical distance between ankles normalized by approximate leg length.
     * > 0.06 indicates one leg is cleanly elevated off the ground in single-leg stance.
     */
    fun getSingleLegBalanceElevation(landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        val leftAnkle = landmarks[PoseLandmarks.LEFT_ANKLE] ?: return null
        val rightAnkle = landmarks[PoseLandmarks.RIGHT_ANKLE] ?: return null
        val leftHip = landmarks[PoseLandmarks.LEFT_HIP] ?: return null

        val legLength = kotlin.math.abs(leftAnkle.y - leftHip.y).toDouble().coerceAtLeast(0.1)
        val ankleDiffY = kotlin.math.abs(leftAnkle.y - rightAnkle.y).toDouble()
        val elevationRatio = (ankleDiffY / legLength) * 100.0 // 0 to 100% elevation

        val confidence = minOf(leftAnkle.visibility, minOf(rightAnkle.visibility, leftHip.visibility))
        return JointAngle(
            jointName = "single_leg_elevation",
            angleDegrees = elevationRatio,
            confidence = confidence,
            timestampMs = timestampMs
        )
    }
}
