package com.kinexmed

import com.kinexmed.domain.controller.PoseAnalysisController
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.registry.ExerciseRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PoseAnalysisControllerTest {

    private lateinit var controller: PoseAnalysisController

    @Before
    fun setUp() {
        val def = ExerciseRegistry.getById("squat")
        val fsm = ExerciseRegistry.createStateMachine(ExerciseType.SQUAT)
        controller = PoseAnalysisController(def, fsm)
    }

    @Test
    fun testInitialStateAndReset() {
        assertEquals(180.0, controller.minAngleSeenOverall, 0.001)
        assertEquals(0.0, controller.maxAngleSeenOverall, 0.001)
        assertEquals(180.0, controller.lastKnownAngle, 0.001)
        assertTrue(controller.evidenceEpisodes.isEmpty())

        controller.reset()
        assertEquals(180.0, controller.minAngleSeenOverall, 0.001)
        assertEquals(0.0, controller.maxAngleSeenOverall, 0.001)
    }

    @Test
    fun testInsufficientEvidenceGeneratesEpisode() {
        // Empty landmarks map
        val result = controller.processFrame(emptyMap(), 1000L)
        assertNull(result.smoothedAngle)
        assertNotNull(controller.currentEvidenceEpisode)
        assertEquals(1000L, controller.currentEvidenceEpisode?.startTimeMs)

        // Finalize episodes
        controller.finalizeEpisodes()
        assertNull(controller.currentEvidenceEpisode)
        assertEquals(1, controller.evidenceEpisodes.size)
    }

    @Test
    fun testEvaluationOnlyDoesNotMutateEpisodes() {
        val status = controller.evaluateEvidenceOnly(emptyMap(), 2000L)
        assertTrue(status is com.kinexmed.domain.model.EvidenceStatus.Insufficient)
        assertNull(controller.currentEvidenceEpisode)
        assertTrue(controller.evidenceEpisodes.isEmpty())
    }
}
