package com.kinexmed

import com.kinexmed.domain.model.ExerciseCategory
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.ExerciseValidationStatus
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.PoseLandmarks
import com.kinexmed.domain.registry.ExerciseRegistry
import org.junit.Assert.*
import org.junit.Test

class ExerciseRegistryTest {

    @Test
    fun testAll15ExercisesRegistered() {
        assertEquals("Must register exactly 15 exercises", 15, ExerciseRegistry.exercises.size)
    }

    @Test
    fun testAllFourCategoriesRepresented() {
        val categories = ExerciseRegistry.exercises.map { it.category }.toSet()
        assertTrue(categories.contains(ExerciseCategory.LOWER_BODY))
        assertTrue(categories.contains(ExerciseCategory.UPPER_BODY))
        assertTrue(categories.contains(ExerciseCategory.FUNCTIONAL_MOBILITY))
        assertTrue(categories.contains(ExerciseCategory.BALANCE))
    }

    @Test
    fun testExplicitValidationStatusIntegrity() {
        val sitToStand = ExerciseRegistry.getByType(ExerciseType.SIT_TO_STAND)
        assertEquals(ExerciseValidationStatus.PHYSICALLY_DEMONSTRATED, sitToStand.status)

        val squat = ExerciseRegistry.getByType(ExerciseType.SQUAT)
        assertEquals(ExerciseValidationStatus.IMPLEMENTED_MODULE, squat.status)

        val otherExercises = ExerciseRegistry.exercises.filter { it.type != ExerciseType.SIT_TO_STAND }
        for (ex in otherExercises) {
            assertEquals("${ex.displayName} must be marked IMPLEMENTED_MODULE", ExerciseValidationStatus.IMPLEMENTED_MODULE, ex.status)
        }
    }

    @Test
    fun testAllExercisesInstantiateValidStateMachines() {
        for (ex in ExerciseRegistry.exercises) {
            val fsm = ExerciseRegistry.createStateMachine(ex.type)
            assertNotNull("FSM for ${ex.displayName} must not be null", fsm)
            assertEquals(ex.type, fsm.exerciseType)
            assertTrue("Primary metric name must not be empty", fsm.primaryMetricName.isNotEmpty())
            assertTrue("Target threshold desc must not be empty", fsm.targetThresholdDesc.isNotEmpty())
        }
    }

    @Test
    fun testMetricExtractionOnMockLandmarks() {
        val mockLandmarks = mapOf(
            PoseLandmarks.LEFT_SHOULDER to Landmark(PoseLandmarks.LEFT_SHOULDER, 0.4f, 0.2f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_SHOULDER to Landmark(PoseLandmarks.RIGHT_SHOULDER, 0.6f, 0.2f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_ELBOW to Landmark(PoseLandmarks.LEFT_ELBOW, 0.35f, 0.35f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_ELBOW to Landmark(PoseLandmarks.RIGHT_ELBOW, 0.65f, 0.35f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_WRIST to Landmark(PoseLandmarks.LEFT_WRIST, 0.35f, 0.5f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_WRIST to Landmark(PoseLandmarks.RIGHT_WRIST, 0.65f, 0.5f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_HIP to Landmark(PoseLandmarks.LEFT_HIP, 0.45f, 0.5f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_HIP to Landmark(PoseLandmarks.RIGHT_HIP, 0.55f, 0.5f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_KNEE to Landmark(PoseLandmarks.LEFT_KNEE, 0.45f, 0.7f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_KNEE to Landmark(PoseLandmarks.RIGHT_KNEE, 0.55f, 0.7f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_ANKLE to Landmark(PoseLandmarks.LEFT_ANKLE, 0.45f, 0.9f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_ANKLE to Landmark(PoseLandmarks.RIGHT_ANKLE, 0.55f, 0.9f, 0.0f, 0.9f),
            PoseLandmarks.LEFT_FOOT_INDEX to Landmark(PoseLandmarks.LEFT_FOOT_INDEX, 0.45f, 0.95f, 0.0f, 0.9f),
            PoseLandmarks.RIGHT_FOOT_INDEX to Landmark(PoseLandmarks.RIGHT_FOOT_INDEX, 0.55f, 0.95f, 0.0f, 0.9f)
        )

        for (ex in ExerciseRegistry.exercises) {
            val metric = ExerciseRegistry.extractMetric(ex.type, mockLandmarks, 1000L)
            assertNotNull("Metric for ${ex.displayName} must extract from valid full-body pose", metric)
            assertTrue("Angle degrees must be non-negative", metric!!.angleDegrees >= 0.0)
            assertTrue("Confidence must be > 0", metric.confidence > 0.0f)
        }
    }
}
