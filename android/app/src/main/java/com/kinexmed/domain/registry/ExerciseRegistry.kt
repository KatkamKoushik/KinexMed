package com.kinexmed.domain.registry

import com.kinexmed.domain.fsm.BalanceHoldFsm
import com.kinexmed.domain.fsm.CalfRaiseStateMachine
import com.kinexmed.domain.fsm.ExerciseStateMachine
import com.kinexmed.domain.fsm.FlexionDirection
import com.kinexmed.domain.fsm.FlexionExtensionConfig
import com.kinexmed.domain.fsm.GenericFlexionExtensionFsm
import com.kinexmed.domain.fsm.LungeStateMachine
import com.kinexmed.domain.fsm.SitToStandStateMachine
import com.kinexmed.domain.fsm.SquatStateMachine
import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.ExerciseCategory
import com.kinexmed.domain.model.ExerciseDefinition
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.ExerciseValidationStatus
import com.kinexmed.domain.model.JointAngle
import com.kinexmed.domain.model.Landmark

/**
 * Central exercise registry for KinexMed.
 * Provides unified discovery of all 15 supported rehabilitation exercises, clinical instructions,
 * target metrics, geometry extraction, and state machine instantiation.
 */
object ExerciseRegistry {

    val exercises: List<ExerciseDefinition> = listOf(
        // ==========================================
        // 1. LOWER BODY EXERCISES
        // ==========================================
        ExerciseDefinition(
            type = ExerciseType.SIT_TO_STAND,
            displayName = "Sit-to-Stand",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.PHYSICALLY_DEMONSTRATED,
            shortDescription = "Rise from a chair to a complete standing position (≥148° knee extension), hold briefly, and sit down under control.",
            instructions = listOf(
                "Sit on a sturdy chair with feet flat on the floor.",
                "Lean slightly forward from the hips.",
                "Push through your feet to stand completely straight.",
                "Pause briefly at full height, then sit back down with control."
            ),
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Chest height, sagittal/side view",
            requiredVisibility = "Full body including chair and feet",
            targetMetricName = "Knee Extension",
            targetMetricTarget = "≥148°",
            primaryJointName = "knee_hip"
        ),
        ExerciseDefinition(
            type = ExerciseType.SQUAT,
            displayName = "Bilateral Squat",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Lower hips until thighs approach parallel (≤100° flexion), then push through heels back to upright standing (≥138°).",
            instructions = listOf(
                "Stand upright with feet shoulder-width apart.",
                "Keep chest upright and look straight ahead.",
                "Lower hips down until knees reach ~90° depth.",
                "Push through heels to return fully to standing."
            ),
            cameraDistance = "2.0 – 3.0 m",
            cameraPlacement = "Hip height, front or 45° angle",
            requiredVisibility = "Full body (head to toes)",
            targetMetricName = "Knee Angle",
            targetMetricTarget = "90° (≤100°)",
            primaryJointName = "knee"
        ),
        ExerciseDefinition(
            type = ExerciseType.FORWARD_LUNGE,
            displayName = "Forward Lunge",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Step forward into a lunge until the front knee reaches approximately 90° flexion, then push back to standing upright.",
            instructions = listOf(
                "Start with feet together, standing tall.",
                "Take a controlled step forward into a lunge.",
                "Lower until the lead knee flexes to ~90°.",
                "Push firmly off the lead foot to return to standing."
            ),
            cameraDistance = "2.5 – 3.2 m",
            cameraPlacement = "Hip height, 45° or side view",
            requiredVisibility = "Full body with clear ground view",
            targetMetricName = "Lead Knee Angle",
            targetMetricTarget = "90° (≤100°)",
            primaryJointName = "lead_knee"
        ),
        ExerciseDefinition(
            type = ExerciseType.REVERSE_LUNGE,
            displayName = "Reverse Lunge",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Step backward into a lunge until the front knee reaches ~90° flexion, then drive through the front foot to return.",
            instructions = listOf(
                "Stand tall with feet hip-width apart.",
                "Step one foot backward and lower hips down.",
                "Lower until front thigh is parallel to ground.",
                "Push through front heel to step back to starting position."
            ),
            cameraDistance = "2.5 – 3.2 m",
            cameraPlacement = "Hip height, sagittal or 45° view",
            requiredVisibility = "Full body with clear ground view",
            targetMetricName = "Lead Knee Angle",
            targetMetricTarget = "90° (≤100°)",
            primaryJointName = "lead_knee"
        ),
        ExerciseDefinition(
            type = ExerciseType.CALF_RAISE,
            displayName = "Calf Raise",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "From flat standing, raise heels high onto balls of feet (ankle angle ≥115°), hold peak, then lower slowly back down.",
            instructions = listOf(
                "Stand upright with feet hip-width apart.",
                "Slowly rise up onto the balls of both feet.",
                "Hold peak heel elevation for 1 second.",
                "Lower your heels smoothly back to the floor."
            ),
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Knee/hip height, front or side view",
            requiredVisibility = "Full legs, ankles, and feet clearly in frame",
            targetMetricName = "Ankle Angle",
            targetMetricTarget = "≥115°",
            primaryJointName = "ankle"
        ),
        ExerciseDefinition(
            type = ExerciseType.KNEE_EXTENSION,
            displayName = "Seated Knee Extension",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "From seated position, extend lower leg forward until knee is nearly straight (≥145°), hold, and lower under control.",
            instructions = listOf(
                "Sit upright in a chair with knees bent at 90°.",
                "Slowly kick one lower leg out straight ahead.",
                "Hold the leg straight at peak for 1 second.",
                "Lower the foot slowly back to the floor."
            ),
            cameraDistance = "2.0 – 2.5 m",
            cameraPlacement = "Knee height, side view",
            requiredVisibility = "Full chair, legs, and feet",
            targetMetricName = "Knee Extension",
            targetMetricTarget = "≥145°",
            primaryJointName = "knee"
        ),
        ExerciseDefinition(
            type = ExerciseType.HIP_ABDUCTION,
            displayName = "Standing Hip Abduction",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "While standing tall with support, raise one leg outward to the side (≤150°), pause, and return smoothly.",
            instructions = listOf(
                "Stand straight, lightly holding a chair or wall for balance.",
                "Keep torso upright and pelvis level.",
                "Lift one leg out to the side without leaning.",
                "Lower leg slowly back to starting position."
            ),
            cameraDistance = "2.0 – 3.0 m",
            cameraPlacement = "Hip height, frontal view",
            requiredVisibility = "Full body from head to feet",
            targetMetricName = "Hip Abduction Angle",
            targetMetricTarget = "≤150°",
            primaryJointName = "hip"
        ),
        ExerciseDefinition(
            type = ExerciseType.HIP_EXTENSION,
            displayName = "Standing Hip Extension",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "While holding support, extend one leg straight back behind you without arching lower back, pause, and return.",
            instructions = listOf(
                "Stand upright holding support, knees slightly soft.",
                "Engage glute and squeeze leg straight back.",
                "Keep back straight and avoid hyperextending spine.",
                "Slowly return foot back to starting position."
            ),
            cameraDistance = "2.2 – 3.0 m",
            cameraPlacement = "Hip height, sagittal/side view",
            requiredVisibility = "Full body in profile view",
            targetMetricName = "Hip Extension Angle",
            targetMetricTarget = "≤152°",
            primaryJointName = "hip"
        ),

        // ==========================================
        // 2. UPPER BODY EXERCISES
        // ==========================================
        ExerciseDefinition(
            type = ExerciseType.SHOULDER_FLEXION,
            displayName = "Shoulder Flexion",
            category = ExerciseCategory.UPPER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Raise arm straight ahead in front of body from hip level up towards horizontal or overhead (≥90°), then lower slowly.",
            instructions = listOf(
                "Stand or sit tall with arms resting at sides.",
                "Keep elbow straight and thumb pointing up.",
                "Raise arm forward and upward until parallel or higher.",
                "Lower arm slowly back to side with control."
            ),
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Chest height, sagittal or 45° view",
            requiredVisibility = "Torso, shoulders, and full arm range",
            targetMetricName = "Shoulder Angle",
            targetMetricTarget = "≥90°",
            primaryJointName = "shoulder"
        ),
        ExerciseDefinition(
            type = ExerciseType.SHOULDER_ABDUCTION,
            displayName = "Shoulder Abduction",
            category = ExerciseCategory.UPPER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Raise arm out to the side away from the body from hip level up to shoulder height or above (≥90°), then lower.",
            instructions = listOf(
                "Stand upright with arms resting at your sides.",
                "Keep your torso steady and shoulders relaxed.",
                "Raise arm out to the side until parallel to floor.",
                "Smoothly lower arm back to your side."
            ),
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Chest height, frontal view",
            requiredVisibility = "Full torso, shoulders, and both arms",
            targetMetricName = "Shoulder Angle",
            targetMetricTarget = "≥90°",
            primaryJointName = "shoulder"
        ),
        ExerciseDefinition(
            type = ExerciseType.ELBOW_FLEXION,
            displayName = "Elbow Flexion (Bicep Curl)",
            category = ExerciseCategory.UPPER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "From fully extended arm at side, curl hand upward toward shoulder (≤70° elbow angle), pause, and lower slowly.",
            instructions = listOf(
                "Stand or sit tall with elbow pinned near your torso.",
                "Start with arm fully extended downward.",
                "Curl hand up toward shoulder by bending elbow.",
                "Lower hand smoothly back down until arm is straight."
            ),
            cameraDistance = "1.5 – 2.2 m",
            cameraPlacement = "Chest height, frontal or 45° view",
            requiredVisibility = "Upper body from hip to head with clear arm view",
            targetMetricName = "Elbow Angle",
            targetMetricTarget = "≤70°",
            primaryJointName = "elbow"
        ),
        ExerciseDefinition(
            type = ExerciseType.ELBOW_EXTENSION,
            displayName = "Elbow Extension (Tricep)",
            category = ExerciseCategory.UPPER_BODY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "From bent elbow position, extend arm straight backward/downward to full extension (≥145°), pause, and bend back.",
            instructions = listOf(
                "Hinge slightly at hips or stand upright with elbow bent at 90°.",
                "Keep upper arm still along your side.",
                "Straighten arm completely back until elbow locks out.",
                "Bend elbow slowly back to 90° starting position."
            ),
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Chest height, sagittal/side view",
            requiredVisibility = "Torso, shoulder, elbow, and wrist in profile",
            targetMetricName = "Elbow Extension",
            targetMetricTarget = "≥145°",
            primaryJointName = "elbow"
        ),

        // ==========================================
        // 3. FUNCTIONAL & MOBILITY
        // ==========================================
        ExerciseDefinition(
            type = ExerciseType.MARCHING_IN_PLACE,
            displayName = "Marching in Place",
            category = ExerciseCategory.FUNCTIONAL_MOBILITY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Alternately lift each knee upward toward hip height (hip angle ≤110°), pause briefly, and step back down.",
            instructions = listOf(
                "Stand tall with feet hip-width apart.",
                "Lift one knee up toward waist height.",
                "Lower foot under control, then lift opposite knee.",
                "Keep rhythm steady and chest lifted."
            ),
            cameraDistance = "2.2 – 3.0 m",
            cameraPlacement = "Hip height, frontal or 45° view",
            requiredVisibility = "Full body from head to feet",
            targetMetricName = "Hip Flexion Angle",
            targetMetricTarget = "≤110°",
            primaryJointName = "hip"
        ),
        ExerciseDefinition(
            type = ExerciseType.HEEL_TOE_RAISE,
            displayName = "Heel-to-Toe Rocking",
            category = ExerciseCategory.FUNCTIONAL_MOBILITY,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Rock smoothly up onto toes into plantarflexion (≥112°), then roll back onto heels, improving ankle mobility.",
            instructions = listOf(
                "Stand tall near a wall or chair for light balance support.",
                "Roll forward onto toes and hold briefly.",
                "Roll back onto heels while gently lifting toes.",
                "Maintain steady, fluid rocking rhythm."
            ),
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Knee height, sagittal/side view",
            requiredVisibility = "Lower legs, ankles, and feet clearly visible",
            targetMetricName = "Ankle Motion",
            targetMetricTarget = "≥112°",
            primaryJointName = "ankle"
        ),

        // ==========================================
        // 4. BALANCE & STABILITY
        // ==========================================
        ExerciseDefinition(
            type = ExerciseType.SINGLE_LEG_BALANCE,
            displayName = "Supported Single-Leg Balance",
            category = ExerciseCategory.BALANCE,
            status = ExerciseValidationStatus.IMPLEMENTED_MODULE,
            shortDescription = "Stand on one leg with light fingertip support nearby, maintaining balance for prescribed target duration (≥10s).",
            instructions = listOf(
                "Stand upright near a sturdy surface (counter or chair).",
                "Shift weight onto standing leg and lift opposite foot.",
                "Keep standing knee soft, eyes focused on a fixed point.",
                "Maintain balance for the target duration."
            ),
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Hip height, frontal view",
            requiredVisibility = "Full body from head to toes",
            targetMetricName = "Balance Hold",
            targetMetricTarget = "≥10s",
            primaryJointName = "single_leg_elevation"
        )
    )

    fun getByType(type: ExerciseType): ExerciseDefinition {
        return exercises.find { it.type == type } ?: exercises.first()
    }

    fun getById(id: String): ExerciseDefinition {
        val direct = exercises.find { it.type.id.equals(id, ignoreCase = true) }
        if (direct != null) return direct

        // Backward compatibility fallbacks
        return when {
            id.equals("lunge", ignoreCase = true) -> getByType(ExerciseType.FORWARD_LUNGE)
            id.contains("stand", ignoreCase = true) -> getByType(ExerciseType.SIT_TO_STAND)
            else -> exercises.first()
        }
    }

    fun getByCategory(category: ExerciseCategory): List<ExerciseDefinition> {
        return exercises.filter { it.category == category }
    }

    fun createStateMachine(type: ExerciseType): ExerciseStateMachine {
        return when (type) {
            ExerciseType.SIT_TO_STAND -> SitToStandStateMachine()
            ExerciseType.SQUAT -> SquatStateMachine()
            ExerciseType.FORWARD_LUNGE -> LungeStateMachine()
            ExerciseType.REVERSE_LUNGE -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.REVERSE_LUNGE,
                    primaryMetricName = "Lead Knee Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.DECREASING,
                    startThreshold = 140.0,
                    targetThreshold = 90.0,
                    minValidThreshold = 105.0,
                    returnThreshold = 145.0,
                    activePrompt = "Step back into lunge...",
                    returnPrompt = "Return to standing"
                )
            )
            ExerciseType.CALF_RAISE -> CalfRaiseStateMachine()
            ExerciseType.KNEE_EXTENSION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.KNEE_EXTENSION,
                    primaryMetricName = "Knee Extension",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.INCREASING,
                    startThreshold = 115.0,
                    targetThreshold = 160.0,
                    minValidThreshold = 145.0,
                    returnThreshold = 115.0,
                    activePrompt = "Extend leg straight forward...",
                    returnPrompt = "Lower foot back down"
                )
            )
            ExerciseType.HIP_ABDUCTION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.HIP_ABDUCTION,
                    primaryMetricName = "Hip Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.DECREASING,
                    startThreshold = 165.0,
                    targetThreshold = 140.0,
                    minValidThreshold = 150.0,
                    returnThreshold = 168.0,
                    activePrompt = "Lift leg out to side...",
                    returnPrompt = "Return leg to center"
                )
            )
            ExerciseType.HIP_EXTENSION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.HIP_EXTENSION,
                    primaryMetricName = "Hip Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.DECREASING,
                    startThreshold = 165.0,
                    targetThreshold = 145.0,
                    minValidThreshold = 152.0,
                    returnThreshold = 168.0,
                    activePrompt = "Extend leg straight back...",
                    returnPrompt = "Return leg to center"
                )
            )
            ExerciseType.MARCHING_IN_PLACE -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.MARCHING_IN_PLACE,
                    primaryMetricName = "Hip Flexion",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.DECREASING,
                    startThreshold = 150.0,
                    targetThreshold = 95.0,
                    minValidThreshold = 110.0,
                    returnThreshold = 155.0,
                    activePrompt = "Drive knee up high...",
                    returnPrompt = "Place foot down"
                )
            )
            ExerciseType.SHOULDER_FLEXION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.SHOULDER_FLEXION,
                    primaryMetricName = "Shoulder Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.INCREASING,
                    startThreshold = 40.0,
                    targetThreshold = 150.0,
                    minValidThreshold = 90.0,
                    returnThreshold = 35.0,
                    activePrompt = "Raise arm straight ahead...",
                    returnPrompt = "Lower arm to side"
                )
            )
            ExerciseType.SHOULDER_ABDUCTION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.SHOULDER_ABDUCTION,
                    primaryMetricName = "Shoulder Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.INCREASING,
                    startThreshold = 40.0,
                    targetThreshold = 150.0,
                    minValidThreshold = 90.0,
                    returnThreshold = 35.0,
                    activePrompt = "Raise arm out to side...",
                    returnPrompt = "Lower arm to side"
                )
            )
            ExerciseType.ELBOW_FLEXION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.ELBOW_FLEXION,
                    primaryMetricName = "Elbow Angle",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.DECREASING,
                    startThreshold = 140.0,
                    targetThreshold = 50.0,
                    minValidThreshold = 70.0,
                    returnThreshold = 145.0,
                    activePrompt = "Curl hand toward shoulder...",
                    returnPrompt = "Lower arm down"
                )
            )
            ExerciseType.ELBOW_EXTENSION -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.ELBOW_EXTENSION,
                    primaryMetricName = "Elbow Extension",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.INCREASING,
                    startThreshold = 90.0,
                    targetThreshold = 160.0,
                    minValidThreshold = 145.0,
                    returnThreshold = 95.0,
                    activePrompt = "Extend arm straight back...",
                    returnPrompt = "Bend elbow to start"
                )
            )
            ExerciseType.HEEL_TOE_RAISE -> GenericFlexionExtensionFsm(
                FlexionExtensionConfig(
                    exerciseType = ExerciseType.HEEL_TOE_RAISE,
                    primaryMetricName = "Ankle Motion",
                    primaryMetricUnit = "°",
                    direction = FlexionDirection.INCREASING,
                    startThreshold = 100.0,
                    targetThreshold = 120.0,
                    minValidThreshold = 112.0,
                    returnThreshold = 95.0,
                    activePrompt = "Rock up onto toes...",
                    returnPrompt = "Return to flat feet"
                )
            )
            ExerciseType.SINGLE_LEG_BALANCE -> BalanceHoldFsm(targetHoldSeconds = 10.0)
        }
    }

    fun extractMetric(type: ExerciseType, landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        return when (type) {
            ExerciseType.SIT_TO_STAND -> GeometryEngine.getPrimaryKneeAngle(landmarks, timestampMs)
            ExerciseType.SQUAT -> GeometryEngine.getPrimaryKneeAngle(landmarks, timestampMs)
            ExerciseType.FORWARD_LUNGE -> GeometryEngine.getLeadKneeAngle(landmarks, timestampMs)
            ExerciseType.REVERSE_LUNGE -> GeometryEngine.getLeadKneeAngle(landmarks, timestampMs)
            ExerciseType.CALF_RAISE -> GeometryEngine.getPrimaryAnkleAngle(landmarks, timestampMs)
            ExerciseType.KNEE_EXTENSION -> GeometryEngine.getSeatedKneeExtensionAngle(landmarks, timestampMs)
            ExerciseType.HIP_ABDUCTION -> GeometryEngine.getStandingHipAbductionAngle(landmarks, timestampMs)
            ExerciseType.HIP_EXTENSION -> GeometryEngine.getStandingHipExtensionAngle(landmarks, timestampMs)
            ExerciseType.MARCHING_IN_PLACE -> GeometryEngine.getMarchingHipFlexionAngle(landmarks, timestampMs)
            ExerciseType.SHOULDER_FLEXION -> GeometryEngine.getPrimaryShoulderAngle(landmarks, timestampMs)
            ExerciseType.SHOULDER_ABDUCTION -> GeometryEngine.getPrimaryShoulderAngle(landmarks, timestampMs)
            ExerciseType.ELBOW_FLEXION -> GeometryEngine.getPrimaryElbowAngle(landmarks, timestampMs)
            ExerciseType.ELBOW_EXTENSION -> GeometryEngine.getPrimaryElbowAngle(landmarks, timestampMs)
            ExerciseType.HEEL_TOE_RAISE -> GeometryEngine.getPrimaryAnkleAngle(landmarks, timestampMs)
            ExerciseType.SINGLE_LEG_BALANCE -> GeometryEngine.getSingleLegBalanceElevation(landmarks, timestampMs)
        }
    }
}
