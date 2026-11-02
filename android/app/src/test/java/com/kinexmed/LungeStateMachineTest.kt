package com.kinexmed

import com.kinexmed.domain.fsm.LungeRuleConfig
import com.kinexmed.domain.fsm.LungeStateMachine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests covering clinical transitions and validation rules
 * for LungeStateMachine.
 */
class LungeStateMachineTest {

    private lateinit var fsm: LungeStateMachine
    private val config = LungeRuleConfig(
        standingExtensionAngle = 155.0,
        descentStartThreshold = 145.0,
        minValidDepthAngle = 100.0,
        targetDepthAngle = 90.0,
        completionExtensionAngle = 150.0,
        minRepDurationMs = 800L,
        maxRepDurationMs = 7000L,
        hysteresisDwellFrames = 2,
        evidenceGracePeriodMs = 1000L
    )

    @Before
    fun setUp() {
        fsm = LungeStateMachine(config)
    }

    private fun feedStanding(startTimeMs: Long = 0L, count: Int = 3): Long {
        var t = startTimeMs
        repeat(count) {
            t += 50
            fsm.update(emptyMap(), 160.0, t, EvidenceStatus.Sufficient)
        }
        return t
    }

    @Test
    fun test1_complete_lunge_equals_one_valid_rep() {
        var t = feedStanding()
        assertEquals(ExerciseState.START, fsm.currentState)

        // Step down into lunge (lead knee flexes from 160° down to 88°)
        val descentAngles = doubleArrayOf(140.0, 130.0, 120.0, 110.0, 98.0, 88.0)
        for (angle in descentAngles) {
            t += 180
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Push back up to upright standing
        val ascentAngles = doubleArrayOf(95.0, 110.0, 125.0, 140.0, 153.0)
        var repRecorded = false
        for (angle in ascentAngles) {
            t += 180
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertTrue("Rep should be clinically valid", res.completedRep!!.isValid)
                assertEquals(1, res.completedRep!!.repNumber)
                assertTrue("Peak depth should satisfy threshold", res.completedRep!!.peakKneeAngle <= 100.0)
            }
        }

        assertTrue("Completed rep must be emitted", repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(1, fsm.validRepCounter)
    }

    @Test
    fun test2_shallow_lunge_marked_invalid() {
        var t = feedStanding()

        // Step only down to 118° (above minValidDepthAngle of 100°)
        val descentAngles = doubleArrayOf(140.0, 130.0, 118.0, 118.0)
        for (angle in descentAngles) {
            t += 200
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Push back up to standing
        val ascentAngles = doubleArrayOf(125.0, 138.0, 152.0)
        var repRecorded = false
        for (angle in ascentAngles) {
            t += 200
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertFalse("Shallow lunge must be marked invalid", res.completedRep!!.isValid)
                assertTrue(res.completedRep!!.failureReasons.any { it.contains("Insufficient depth") })
            }
        }

        assertTrue(repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }
}
