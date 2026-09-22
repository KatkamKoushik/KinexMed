package com.kinexmed.domain.registry

import com.kinexmed.domain.fsm.CalfRaiseStateMachine
import com.kinexmed.domain.fsm.ExerciseStateMachine
import com.kinexmed.domain.fsm.LungeStateMachine
import com.kinexmed.domain.fsm.SitToStandStateMachine
import com.kinexmed.domain.fsm.SquatStateMachine
import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.ExerciseDefinition
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.ExerciseValidationStatus
import com.kinexmed.domain.model.JointAngle
import com.kinexmed.domain.model.Landmark

/**
 * Central exercise registry for KinexMed.
 * Provides unified discovery of supported rehabilitation exercises, instructions,
 * target metrics, geometry extraction, and state machine instantiation.
 */
object ExerciseRegistry {

    val exercises: List<ExerciseDefinition> = listOf(
        ExerciseDefinition(
            type = ExerciseType.SQUAT,
            displayName = "Bilateral Squat",
            status = ExerciseValidationStatus.VALIDATED,
            shortDescription = "Lower hips until thighs are parallel to ground (≤100° flexion), then push through heels back to upright standing (≥142° extension).",
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
            type = ExerciseType.SIT_TO_STAND,
            displayName = "Sit-to-Stand",
            status = ExerciseValidationStatus.VALIDATED,
            shortDescription = "Rise from a seated chair position to a complete upright standing position (≥155°), hold briefly, and sit back down under control.",
            instructions = listOf(
                "Sit on a sturdy chair with feet flat on the floor.",
                "Lean slightly forward from the hips.",
                "Push through feet to stand up completely straight.",
                "Pause briefly at full height, then sit back down with control."
            ),
            cameraDistance = "2.0 – 2.8 m",
            cameraPlacement = "Chest height, sagittal/side view",
            requiredVisibility = "Full body including chair and feet",
            targetMetricName = "Knee Extension",
            targetMetricTarget = "≥155°",
            primaryJointName = "knee_hip"
        ),
        ExerciseDefinition(
            type = ExerciseType.LUNGE,
            displayName = "Reverse/Forward Lunge",
            status = ExerciseValidationStatus.VALIDATED,
            shortDescription = "Step one leg into a lunge until the front knee reaches approximately 90° flexion (≤100°), then push back to bilateral standing.",
            instructions = listOf(
                "Start with feet together, standing tall.",
                "Take a controlled step into a lunge.",
                "Lower until the lead knee flexes to ~90°.",
                "Push firmly off the lead foot to return to standing upright."
            ),
            cameraDistance = "2.5 – 3.2 m",
            cameraPlacement = "Hip height, 45° or side view",
            requiredVisibility = "Full body with clear ground view",
            targetMetricName = "Lead Knee Angle",
            targetMetricTarget = "90° (≤100°)",
            primaryJointName = "lead_knee"
        ),
        ExerciseDefinition(
            type = ExerciseType.CALF_RAISE,
            displayName = "Calf Raise",
            status = ExerciseValidationStatus.VALIDATED,
            shortDescription = "From standing flat on floor, raise heels high onto balls of feet (ankle angle ≥120°), hold peak, then lower slowly back down.",
            instructions = listOf(
                "Stand upright with feet hip-width apart.",
                "Slowly rise up onto the balls of both feet.",
                "Hold peak heel elevation for 1 second.",
                "Lower your heels smoothly back to the floor."
            ),
            cameraDistance = "1.8 – 2.5 m",
            cameraPlacement = "Knee/hip height, front or side view",
            requiredVisibility = "Full legs, ankles, and feet clearly in frame",
            targetMetricName = "Ankle Plantarflexion",
            targetMetricTarget = "≥120°",
            primaryJointName = "ankle"
        )
    )

    fun getByType(type: ExerciseType): ExerciseDefinition {
        return exercises.find { it.type == type } ?: exercises.first()
    }

    fun getById(id: String): ExerciseDefinition {
        return exercises.find { it.type.id.equals(id, ignoreCase = true) } ?: exercises.first()
    }

    fun createStateMachine(type: ExerciseType): ExerciseStateMachine {
        return when (type) {
            ExerciseType.SQUAT -> SquatStateMachine()
            ExerciseType.SIT_TO_STAND -> SitToStandStateMachine()
            ExerciseType.LUNGE -> LungeStateMachine()
            ExerciseType.CALF_RAISE -> CalfRaiseStateMachine()
        }
    }

    fun extractMetric(type: ExerciseType, landmarks: Map<Int, Landmark>, timestampMs: Long): JointAngle? {
        return when (type) {
            ExerciseType.SQUAT -> GeometryEngine.getPrimaryKneeAngle(landmarks, timestampMs)
            ExerciseType.SIT_TO_STAND -> GeometryEngine.getPrimaryKneeAngle(landmarks, timestampMs)
            ExerciseType.LUNGE -> GeometryEngine.getLeadKneeAngle(landmarks, timestampMs)
            ExerciseType.CALF_RAISE -> GeometryEngine.getPrimaryAnkleAngle(landmarks, timestampMs)
        }
    }
}
