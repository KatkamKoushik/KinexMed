package com.kinexmed

import com.kinexmed.domain.fsm.SitToStandRuleConfig
import com.kinexmed.domain.fsm.SitToStandStateMachine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests covering clinical transitions and validation rules
 * for SitToStandStateMachine.
 */
class SitToStandStateMachineTest {

    private lateinit var fsm: SitToStandStateMachine
    private val config = SitToStandRuleConfig(
        seatedKneeMaxAngle = 105.0,
        standingKneeMinAngle = 155.0,
        standingHipMinAngle = 150.0,
        minRepDurationMs = 800L,
        maxRepDurationMs = 8000L,
        evidenceGracePeriodMs = 1000L
    )

    @Before
    fun setUp() {
        fsm = SitToStandStateMachine(config)
    }

    private fun feedSeated(startTimeMs: Long = 0L, count: Int = 3): Long {
        var t = startTimeMs
        repeat(count) {
            t += 50
            fsm.update(emptyMap(), 90.0, t, EvidenceStatus.Sufficient)
        }
        return t
    }

    @Test
    fun test1_complete_sit_to_stand_equals_one_valid_rep() {
        var t = feedSeated()
        assertEquals(ExerciseState.START, fsm.currentState)

        // Rise from seated (90°) to full upright extension (165°)
        val riseAngles = doubleArrayOf(115.0, 125.0, 135.0, 145.0, 155.0, 165.0)
        for (angle in riseAngles) {
            t += 200
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Return back to seated position (90°)
        val returnAngles = doubleArrayOf(150.0, 135.0, 120.0, 105.0, 92.0)
        var repRecorded = false
        for (angle in returnAngles) {
            t += 200
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertTrue("Rep should be clinically valid", res.completedRep!!.isValid)
                assertEquals(1, res.completedRep!!.repNumber)
                assertTrue("Peak extension should meet or exceed target", res.completedRep!!.peakKneeAngle >= 155.0)
            }
        }

        assertTrue("Completed rep must be emitted", repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(1, fsm.validRepCounter)
    }

    @Test
    fun test2_insufficient_extension_marked_invalid() {
        var t = feedSeated()

        // Rise only to 135° (below standing threshold of 155°)
        val riseAngles = doubleArrayOf(115.0, 125.0, 135.0, 135.0)
        for (angle in riseAngles) {
            t += 250
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Return back to seated position
        val returnAngles = doubleArrayOf(130.0, 120.0, 105.0, 90.0)
        var repRecorded = false
        for (angle in returnAngles) {
            t += 250
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertFalse("Rep with insufficient extension must be marked invalid", res.completedRep!!.isValid)
                assertTrue(res.completedRep!!.failureReasons.any { it.contains("Insufficient stand") })
            }
        }

        assertTrue(repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }

    @Test
    fun test3_too_fast_repetition_rejected() {
        var t = feedSeated()

        // Ultra fast rising and lowering in 420ms (below minRepDurationMs of 800ms)
        val angles = doubleArrayOf(115.0, 125.0, 160.0, 135.0, 102.0, 90.0)
        var repRecorded = false
        for (angle in angles) {
            t += 70
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertFalse("Sub-second rapid movement must be marked invalid", res.completedRep!!.isValid)
                assertTrue(res.completedRep!!.failureReasons.any { it.contains("too fast") })
            }
        }

        assertTrue(repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }
}
