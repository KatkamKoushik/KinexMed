package com.kinexmed

import com.kinexmed.domain.fsm.SquatStateMachine
import com.kinexmed.domain.model.EvidenceFailureReason
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.rules.SquatRuleConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests covering all 10 clinical and biomechanical edge cases
 * for the KinexMed SquatStateMachine.
 */
class SquatStateMachineTest {

    private lateinit var fsm: SquatStateMachine
    private val config = SquatRuleConfig(
        standingExtensionAngle = 145.0,
        descentStartThreshold = 135.0,
        targetDepthAngle = 90.0,
        minValidDepthAngle = 100.0,
        ascentStartThreshold = 105.0,
        completionExtensionAngle = 142.0,
        minRepDurationMs = 600L,
        maxRepDurationMs = 10000L,
        hysteresisDwellFrames = 2,
        evidenceGracePeriodMs = 1500L
    )

    @Before
    fun setUp() {
        fsm = SquatStateMachine(config)
    }

    private fun feedStanding(startTimeMs: Long = 0L, count: Int = 3): Long {
        var t = startTimeMs
        repeat(count) {
            t += 33
            fsm.update(160.0, t, EvidenceStatus.Sufficient)
        }
        return t
    }

    /**
     * Test 1: standing -> squat -> standing = 1 valid rep
     */
    @Test
    fun test1_standing_squat_standing_equals_one_valid_rep() {
        var t = feedStanding()
        assertEquals(ExerciseState.START, fsm.currentState)

        // Descent to 85° (target is 90°, min valid is 100°)
        val descentAngles = doubleArrayOf(140.0, 130.0, 120.0, 110.0, 100.0, 90.0, 85.0)
        for (angle in descentAngles) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        // Ascend back to 155°
        val ascentAngles = doubleArrayOf(88.0, 95.0, 105.0, 115.0, 130.0, 140.0, 155.0, 155.0)
        var repRecorded = false
        for (angle in ascentAngles) {
            t += 100
            val result = fsm.update(angle, t, EvidenceStatus.Sufficient)
            if (result.completedRep != null) {
                repRecorded = true
                assertTrue("Rep should be valid", result.completedRep!!.isValid)
                assertEquals(1, result.completedRep!!.repNumber)
                assertEquals(85.0, result.completedRep!!.peakKneeAngle, 0.5)
            }
        }

        assertTrue("Completed rep must be emitted", repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(1, fsm.validRepCounter)
    }

    /**
     * Test 2: partial squat = 0 valid reps (insufficient depth recorded)
     */
    @Test
    fun test2_partial_squat_insufficient_depth() {
        var t = feedStanding()

        // Descend to only 115° (above minValidDepthAngle of 100°)
        val descentAngles = doubleArrayOf(140.0, 130.0, 120.0, 115.0, 115.0)
        for (angle in descentAngles) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        // Ascend back to standing
        val ascentAngles = doubleArrayOf(122.0, 130.0, 140.0, 155.0, 155.0)
        var repRecorded = false
        for (angle in ascentAngles) {
            t += 100
            val result = fsm.update(angle, t, EvidenceStatus.Sufficient)
            if (result.completedRep != null) {
                repRecorded = true
                assertFalse("Partial squat must be marked invalid", result.completedRep!!.isValid)
                assertTrue("Failure reason must mention insufficient depth",
                    result.completedRep!!.failureReasons.any { it.contains("depth", ignoreCase = true) })
            }
        }

        assertTrue(repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }

    /**
     * Test 3: squat without returning upright = 0 reps
     */
    @Test
    fun test3_squat_without_returning_upright_zero_reps() {
        var t = feedStanding()

        // Descend to 85°
        for (angle in doubleArrayOf(130.0, 110.0, 90.0, 85.0)) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        // Rise to only 125° and stay there (below completionExtensionAngle of 142°)
        for (i in 1..20) {
            t += 50
            val result = fsm.update(125.0, t, EvidenceStatus.Sufficient)
            assertNull("No rep should complete without returning upright", result.completedRep)
        }

        assertEquals(0, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }

    /**
     * Test 4: noisy angle around threshold = no duplicate reps or erratic state flipping
     */
    @Test
    fun test4_noisy_angle_around_threshold_no_duplicate_reps() {
        var t = 0L
        // Jitter around standing threshold
        val jitter = doubleArrayOf(144.8, 145.2, 144.9, 145.1, 144.7, 145.3, 145.0)
        for (angle in jitter) {
            t += 33
            val result = fsm.update(angle, t, EvidenceStatus.Sufficient)
            assertNull(result.completedRep)
            assertEquals(0, result.totalReps)
        }
    }

    /**
     * Test 5: two complete squats = 2 reps
     */
    @Test
    fun test5_two_complete_squats_equals_two_valid_reps() {
        var t = feedStanding()

        // Rep 1 (takes ~1200ms)
        for (angle in doubleArrayOf(130.0, 110.0, 85.0, 88.0, 100.0, 120.0, 145.0, 150.0)) {
            t += 150
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }
        assertEquals(1, fsm.validRepCounter)

        // Rep 2 (takes ~1200ms)
        for (angle in doubleArrayOf(130.0, 100.0, 80.0, 85.0, 105.0, 125.0, 145.0, 150.0)) {
            t += 150
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }
        assertEquals(2, fsm.repCounter)
        assertEquals(2, fsm.validRepCounter)
    }

    /**
     * Test 6: temporary evidence failure does not create a false rep and does not reset active rep
     */
    @Test
    fun test6_temporary_evidence_failure_does_not_create_false_rep() {
        var t = feedStanding()

        // Descend to 95°
        for (angle in doubleArrayOf(130.0, 110.0, 95.0)) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        // Transient evidence failure for 2 frames (66ms < 1500ms grace period)
        t += 33
        fsm.update(95.0, t, EvidenceStatus.Insufficient(EvidenceFailureReason.LOW_CONFIDENCE, "Transient blur"))
        t += 33
        fsm.update(95.0, t, EvidenceStatus.Insufficient(EvidenceFailureReason.LOW_CONFIDENCE, "Transient blur"))

        // Evidence restored, continue squat to 85° and rise to 150°
        for (angle in doubleArrayOf(85.0, 92.0, 110.0, 130.0, 145.0, 150.0)) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        assertEquals(1, fsm.validRepCounter)
    }

    /**
     * Test 7: prolonged invalid evidence resets ongoing rep and prevents counting
     */
    @Test
    fun test7_prolonged_invalid_evidence_prevents_counting() {
        var t = feedStanding()

        // Descend into squat
        for (angle in doubleArrayOf(130.0, 110.0, 95.0)) {
            t += 100
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }
        assertEquals(ExerciseState.LOWERING, fsm.currentState)

        // Prolonged evidence failure (> 1500ms grace period)
        t += 500
        fsm.update(95.0, t, EvidenceStatus.Insufficient(EvidenceFailureReason.LANDMARKS_MISSING, "Out of view"))
        t += 1600 // Elapsed failure time = 1600ms > 1500ms
        fsm.update(95.0, t, EvidenceStatus.Insufficient(EvidenceFailureReason.LANDMARKS_MISSING, "Out of view"))

        assertEquals("FSM must reset to IDLE after prolonged evidence loss", ExerciseState.IDLE, fsm.currentState)
        assertEquals(0, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }

    /**
     * Test 8: fast movement below minRepDurationMs (600ms) completes but is flagged as invalid
     */
    @Test
    fun test8_fast_movement_under_minimum_duration_marked_invalid() {
        var t = feedStanding()

        // Fast descent and ascent in 300ms (< 600ms minimum)
        for (angle in doubleArrayOf(130.0, 85.0, 90.0, 120.0, 150.0, 150.0)) {
            t += 50 // 5 * 50 = 250ms
            val res = fsm.update(angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                assertFalse("Rep under 600ms should be invalid for fast motion", res.completedRep!!.isValid)
                assertTrue(res.completedRep!!.failureReasons.any { it.contains("fast", ignoreCase = true) })
            }
        }
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }

    /**
     * Test 9: slow movement within limit (e.g. 3500ms) completes correctly
     */
    @Test
    fun test9_slow_movement_completes_correctly() {
        var t = feedStanding()

        // Controlled descent (2000ms)
        for (angle in doubleArrayOf(130.0, 120.0, 110.0, 100.0, 90.0, 85.0)) {
            t += 350
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        // Controlled ascent (1500ms)
        for (angle in doubleArrayOf(90.0, 105.0, 120.0, 135.0, 145.0, 150.0)) {
            t += 300
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }

        assertEquals(1, fsm.validRepCounter)
    }

    /**
     * Test 10: COMPLETED state cannot generate multiple reps on subsequent frames
     */
    @Test
    fun test10_completed_state_cannot_generate_multiple_reps() {
        var t = feedStanding()

        // Complete 1 valid squat (takes ~1200ms)
        for (angle in doubleArrayOf(130.0, 110.0, 85.0, 95.0, 115.0, 135.0, 150.0, 150.0)) {
            t += 150
            fsm.update(angle, t, EvidenceStatus.Sufficient)
        }
        assertEquals(1, fsm.repCounter)

        // Continue standing upright for 50 more frames
        for (i in 1..50) {
            t += 33
            val result = fsm.update(160.0, t, EvidenceStatus.Sufficient)
            assertNull("Subsequent standing frames must NOT emit another rep", result.completedRep)
            assertEquals("Total reps must remain 1", 1, result.totalReps)
            assertEquals("Valid reps must remain 1", 1, result.validReps)
        }
    }
}
