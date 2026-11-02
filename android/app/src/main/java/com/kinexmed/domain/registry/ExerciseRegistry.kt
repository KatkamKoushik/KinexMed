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
        // 1. LOWER BODY EXERCISES
        // ==========================================
        ExerciseDefinition(
            type = ExerciseType.SIT_TO_STAND,
            displayName = "Sit-to-Stand",
            category = ExerciseCategory.LOWER_BODY,
            status = ExerciseValidationStatus.PHYSICALLY_DEMONSTRATED,
            shortDescription = "Rise from a chair to a complete standing position (≥148° knee extension), hold briefly, and sit down under control.",
            setupInstructions = "Place a sturdy, armless chair against a wall or non-slip surface.",
            startingPosition = "Sit upright with feet hip-width apart and planted firmly on the floor. Cross arms over chest or rest hands lightly on thighs.",
            movementInstructions = listOf(
                "Lean forward slightly from the hips.",
                "Drive through your feet to stand completely straight.",
                "Reach full upright posture with knee extension ≥148°.",
                "Pause briefly at full height, then lower hips with control back onto the seat."
            ),
            completionInstructions = "Lower hips fully back into the seat before beginning the next repetition.",
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Chest height, sagittal/side view",
            requiredVisibility = "Full body including chair and feet",
            evidenceRequirements = listOf(
                "Full sagittal body view from head to toes",
                "Clear view of chair seat and floor line",
                "Adequate lighting without backlight glare"
            ),
            commonRejectionReasons = listOf(
                "Incomplete knee extension (<148° at standing peak)",
                "Incomplete seat return (<125° knee flexion on chair)",
                "Excessive forward torso momentum or hand push-off"
            ),
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
            setupInstructions = "Clear a 2x2 meter flat space on a level, non-slip surface.",
            startingPosition = "Stand tall with feet shoulder-width apart, toes pointing slightly outward (5-15°).",
            movementInstructions = listOf(
                "Initiate by sending hips backward as knees bend.",
                "Lower until thighs approach parallel (knee flexion ≤100°).",
                "Maintain upright chest and keep heels grounded.",
                "Drive through midfoot and heels to return to full upright standing (≥138°)."
            ),
            completionInstructions = "Lock out hips and knees in full upright posture to complete repetition.",
            cameraDistance = "2.0 – 3.0 m",
            cameraPlacement = "Hip height, front or 45° angle",
            requiredVisibility = "Full body (head to toes)",
            evidenceRequirements = listOf(
                "Full frontal or 45-degree body visibility",
                "Unobstructed view of both knees and ankles",
                "Stable camera height around hip level"
            ),
            commonRejectionReasons = listOf(
                "Insufficient squat depth (>100° at bottom turning point)",
                "Incomplete standing lockout (<138° at top)",
                "Heels lifting off ground"
            ),
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
            setupInstructions = "Clear a 3-meter straight path in front of you.",
            startingPosition = "Stand upright with feet together and hands placed comfortably on hips.",
            movementInstructions = listOf(
                "Take a controlled step forward with lead leg.",
                "Lower until the lead knee flexes to ~90° (≤100°).",
                "Keep torso upright and back knee hovering above floor.",
                "Push firmly off lead foot to return to standing."
            ),
            completionInstructions = "Return lead foot to touch trailing foot with knees fully extended.",
            cameraDistance = "2.5 – 3.2 m",
            cameraPlacement = "Hip height, 45° or side view",
            requiredVisibility = "Full body with clear ground view",
            evidenceRequirements = listOf(
                "Side or 45-degree angle capturing full step length",
                "Ground and feet visible throughout entire stride"
            ),
            commonRejectionReasons = listOf(
                "Shallow lead knee depth (>105°)",
                "Lead knee collapsing inward or excessive torso forward lean",
                "Incomplete return to starting stance"
            ),
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
            setupInstructions = "Ensure clear space behind you for a full backward step.",
            startingPosition = "Stand upright with feet hip-width apart.",
            movementInstructions = listOf(
                "Step one foot backward onto ball of foot.",
                "Lower hips straight down until lead knee reaches ~90° flexion.",
                "Drive through front heel to return to upright standing."
            ),
            completionInstructions = "Bring rear foot forward to meet front foot in neutral stance.",
            cameraDistance = "2.5 – 3.2 m",
            cameraPlacement = "Hip height, sagittal or 45° view",
            requiredVisibility = "Full body with clear ground view",
            evidenceRequirements = listOf(
                "Side sagittal view capturing both legs and hips",
                "Unobstructed lighting on floor plane"
            ),
            commonRejectionReasons = listOf(
                "Insufficient lead knee flexion (>105°)",
                "Pushing off rear foot rather than loading front heel",
                "Loss of balance or torso tilt"
            ),
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
            setupInstructions = "Stand near a wall or sturdy countertop for optional fingertip balance.",
            startingPosition = "Stand upright with feet hip-width apart and equal weight distributed.",
            movementInstructions = listOf(
                "Press through the balls of both feet.",
                "Elevate heels as high as possible into full plantarflexion (≥115°).",
                "Hold peak heel elevation for 1 second.",
                "Lower your heels smoothly back to the floor."
            ),
            completionInstructions = "Touch heels gently to the floor before starting the next rep.",
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Knee/hip height, front or side view",
            requiredVisibility = "Full legs, ankles, and feet clearly in frame",
            evidenceRequirements = listOf(
                "Camera focused at mid-shin to foot level or full body",
                "Clear contrast between feet and flooring"
            ),
            commonRejectionReasons = listOf(
                "Incomplete heel elevation (<115° plantarflexion)",
                "Rapid bouncing without controlled descent",
                "Weight rolling to outer foot edges"
            ),
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
            setupInstructions = "Sit on a sturdy, firm chair with thighs fully supported.",
            startingPosition = "Sit upright with back against backrest, knees bent at 90°, feet resting on floor.",
            movementInstructions = listOf(
                "Slowly kick one lower leg out straight ahead.",
                "Elevate foot until leg reaches terminal extension (≥145°).",
                "Hold the leg straight at peak for 1 second.",
                "Lower the foot slowly back down to starting position."
            ),
            completionInstructions = "Rest foot fully on floor between repetitions.",
            cameraDistance = "2.0 – 2.5 m",
            cameraPlacement = "Knee height, side view",
            requiredVisibility = "Full chair, legs, and feet",
            evidenceRequirements = listOf(
                "Sagittal side view of chair, hip, knee, and ankle",
                "Clear foot clearance from floor"
            ),
            commonRejectionReasons = listOf(
                "Incomplete knee extension (<145°)",
                "Slouching back to cheat range of motion",
                "Dropping leg rapidly without eccentric control"
            ),
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
            setupInstructions = "Stand tall holding a sturdy wall or chair with one hand for light stability.",
            startingPosition = "Stand straight on stationary leg, keeping toes pointing straight forward.",
            movementInstructions = listOf(
                "Engage outer hip and lift outer leg out to the side.",
                "Keep leg straight and pelvis level without leaning torso.",
                "Reach target abduction (hip angle ≤150°).",
                "Lower leg slowly back to starting position."
            ),
            completionInstructions = "Bring moving foot back alongside standing foot.",
            cameraDistance = "2.0 – 3.0 m",
            cameraPlacement = "Hip height, frontal view",
            requiredVisibility = "Full body from head to feet",
            evidenceRequirements = listOf(
                "Frontal coronal view showing both hips and shoulders",
                "Level pelvic line visible"
            ),
            commonRejectionReasons = listOf(
                "Insufficient leg abduction (>150° hip angle)",
                "Torso leaning excessively to opposite side",
                "Rotating leg outward instead of keeping toes forward"
            ),
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
            setupInstructions = "Stand tall facing a sturdy support surface.",
            startingPosition = "Stand upright on one leg with hands lightly resting on support.",
            movementInstructions = listOf(
                "Engage glute and extend leg straight back.",
                "Keep back straight and avoid hyperextending spine (hip angle ≤152°).",
                "Pause briefly at peak extension.",
                "Slowly return foot back to starting position."
            ),
            completionInstructions = "Lower foot back down next to standing leg.",
            cameraDistance = "2.2 – 3.0 m",
            cameraPlacement = "Hip height, sagittal/side view",
            requiredVisibility = "Full body in profile view",
            evidenceRequirements = listOf(
                "Sagittal side profile of hip, spine, and moving leg",
                "Clear view of pelvic tilt"
            ),
            commonRejectionReasons = listOf(
                "Incomplete hip extension (>152° angle)",
                "Excessive hyperextension of lumbar spine",
                "Bending knee rather than extending hip"
            ),
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
            setupInstructions = "Stand or sit tall in a chair with arm hanging naturally.",
            startingPosition = "Arm straight down by side, thumb pointing forward and upward.",
            movementInstructions = listOf(
                "Keep elbow straight and raise arm forward and upward.",
                "Elevate arm until parallel to floor or higher (≥90°).",
                "Pause briefly at the top.",
                "Lower arm slowly back to side with control."
            ),
            completionInstructions = "Return arm to vertical rest position beside torso.",
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Chest height, sagittal or 45° view",
            requiredVisibility = "Torso, shoulders, and full arm range",
            evidenceRequirements = listOf(
                "Side sagittal view capturing shoulder, elbow, wrist, and torso",
                "Unobstructed overhead space"
            ),
            commonRejectionReasons = listOf(
                "Incomplete arm elevation (<90° angle)",
                "Bending elbow during elevation",
                "Shrugging shoulder or arching back"
            ),
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
            setupInstructions = "Stand upright with arm relaxed at your side.",
            startingPosition = "Stand tall with palm facing inward against thigh.",
            movementInstructions = listOf(
                "Keep torso steady and shoulders relaxed.",
                "Raise arm out to the side until parallel to floor or higher (≥90°).",
                "Hold briefly at peak elevation.",
                "Smoothly lower arm back to your side."
            ),
            completionInstructions = "Return arm to resting position against side of body.",
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Chest height, frontal view",
            requiredVisibility = "Full torso, shoulders, and both arms",
            evidenceRequirements = listOf(
                "Frontal coronal view showing full arm span and neck line",
                "Clear lateral clearance around arm"
            ),
            commonRejectionReasons = listOf(
                "Incomplete lateral elevation (<90°)",
                "Excessive trunk lateral flexion",
                "Bending elbow during abduction"
            ),
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
            setupInstructions = "Stand or sit tall with elbow pinned near your torso.",
            startingPosition = "Arm extended straight down, palm facing forward.",
            movementInstructions = listOf(
                "Keep upper arm stationary against ribcage.",
                "Curl hand up toward shoulder by bending elbow (≤70°).",
                "Squeeze bicep at peak flexion.",
                "Lower hand smoothly back down until arm is straight (≥145°)."
            ),
            completionInstructions = "Achieve complete elbow extension before beginning next repetition.",
            cameraDistance = "1.5 – 2.2 m",
            cameraPlacement = "Chest height, frontal or 45° view",
            requiredVisibility = "Upper body from hip to head with clear arm view",
            evidenceRequirements = listOf(
                "Sagittal or 45-degree view of arm and torso",
                "Elbow joint clearly visible without clothing occlusion"
            ),
            commonRejectionReasons = listOf(
                "Insufficient elbow flexion (>70° at top)",
                "Incomplete elbow lockout at bottom (<145°)",
                "Swinging upper arm forward"
            ),
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
            setupInstructions = "Hinge slightly at hips or sit upright with upper arm supported.",
            startingPosition = "Elbow bent at 90° with upper arm aligned along torso.",
            movementInstructions = listOf(
                "Keep upper arm stationary along your side.",
                "Straighten arm completely back until elbow locks out (≥145°).",
                "Pause briefly with tricep engaged.",
                "Bend elbow slowly back to 90° starting position."
            ),
            completionInstructions = "Return forearm to 90° starting position under control.",
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Chest height, sagittal/side view",
            requiredVisibility = "Torso, shoulder, elbow, and wrist in profile",
            evidenceRequirements = listOf(
                "Clear side view of upper arm, elbow, and wrist",
                "Minimal body movement outside arm"
            ),
            commonRejectionReasons = listOf(
                "Incomplete terminal extension (<145°)",
                "Dropping elbow during extension",
                "Using torso momentum"
            ),
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
            setupInstructions = "Stand on a flat, non-slip surface with plenty of vertical room.",
            startingPosition = "Stand tall with feet hip-width apart and arms relaxed at sides.",
            movementInstructions = listOf(
                "Lift one knee up toward waist height (hip angle ≤110°).",
                "Maintain upright posture without leaning back.",
                "Lower foot under control, then lift opposite knee.",
                "Keep rhythm steady and chest lifted."
            ),
            completionInstructions = "Plant foot firmly on ground before initiating next rep.",
            cameraDistance = "2.2 – 3.0 m",
            cameraPlacement = "Hip height, frontal or 45° view",
            requiredVisibility = "Full body from head to feet",
            evidenceRequirements = listOf(
                "Front or 45-degree angle showing both knees and torso",
                "Unobstructed view of pelvic height"
            ),
            commonRejectionReasons = listOf(
                "Insufficient knee height (>110° hip angle)",
                "Leaning backward to lift knee",
                "Stamping foot down aggressively"
            ),
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
            setupInstructions = "Stand tall near a wall or chair for light balance support.",
            startingPosition = "Stand tall with feet hip-width apart and knees soft.",
            movementInstructions = listOf(
                "Roll forward onto toes, lifting heels high (≥112°).",
                "Hold briefly, then roll back onto heels while gently lifting toes.",
                "Maintain steady, fluid rocking rhythm."
            ),
            completionInstructions = "Return to flat, centered feet between reps.",
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Knee height, sagittal/side view",
            requiredVisibility = "Lower legs, ankles, and feet clearly visible",
            evidenceRequirements = listOf(
                "Lower leg and foot framing with clear contrast against floor",
                "Stable lateral foot visibility"
            ),
            commonRejectionReasons = listOf(
                "Insufficient heel/toe elevation",
                "Jerky shifting of hips or balance loss",
                "Bending knees excessively"
            ),
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
            setupInstructions = "Position yourself next to a sturdy chair or wall for safety.",
            startingPosition = "Stand upright on both feet with gaze fixed at eye level.",
            movementInstructions = listOf(
                "Shift weight onto standing leg and lift opposite foot.",
                "Keep standing knee soft, eyes focused on a fixed point.",
                "Maintain balance for target hold duration (≥10s).",
                "Keep hips level throughout the hold."
            ),
            completionInstructions = "Lower lifted foot back down to floor smoothly.",
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Hip height, frontal view",
            requiredVisibility = "Full body from head to toes",
            evidenceRequirements = listOf(
                "Full body frontal view showing head, hips, and foot elevation",
                "Clear ground separation visible under lifted foot"
            ),
            commonRejectionReasons = listOf(
                "Lifted foot touching ground before target duration",
                "Excessive pelvic tilt or body sway",
                "Loss of landmark tracking on foot"
            ),
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
