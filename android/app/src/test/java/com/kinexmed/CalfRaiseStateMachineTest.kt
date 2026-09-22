package com.kinexmed

import com.kinexmed.domain.fsm.CalfRaiseRuleConfig
import com.kinexmed.domain.fsm.CalfRaiseStateMachine
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit tests covering clinical transitions and validation rules
 * for CalfRaiseStateMachine.
 */
class CalfRaiseStateMachineTest {

    private lateinit var fsm: CalfRaiseStateMachine
    private val config = CalfRaiseRuleConfig(
        flatFootMaxAngle = 100.0,
        liftStartThreshold = 108.0,
        minValidPlantarflexionAngle = 120.0,
        targetPlantarflexionAngle = 130.0,
        completionFlatAngle = 102.0,
        minRepDurationMs = 800L,
        maxRepDurationMs = 6000L,
        hysteresisDwellFrames = 2,
        evidenceGracePeriodMs = 1000L
    )

    @Before
    fun setUp() {
        fsm = CalfRaiseStateMachine(config)
    }

    private fun feedFlat(startTimeMs: Long = 0L, count: Int = 3): Long {
        var t = startTimeMs
        repeat(count) {
            t += 50
            fsm.update(emptyMap(), 95.0, t, EvidenceStatus.Sufficient)
        }
        return t
    }

    @Test
    fun test1_complete_calf_raise_equals_one_valid_rep() {
        var t = feedFlat()
        assertEquals(ExerciseState.START, fsm.currentState)

        // Rise onto balls of feet (ankle angle goes from 95° to 132°)
        val riseAngles = doubleArrayOf(105.0, 112.0, 120.0, 128.0, 132.0)
        for (angle in riseAngles) {
            t += 180
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Lower heels back down to floor (95°)
        val lowerAngles = doubleArrayOf(125.0, 115.0, 106.0, 98.0)
        var repRecorded = false
        for (angle in lowerAngles) {
            t += 180
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertTrue("Rep should be clinically valid", res.completedRep!!.isValid)
                assertEquals(1, res.completedRep!!.repNumber)
                assertTrue("Peak heel lift should satisfy threshold", res.completedRep!!.peakKneeAngle >= 120.0)
            }
        }

        assertTrue("Completed rep must be emitted", repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(1, fsm.validRepCounter)
    }

    @Test
    fun test2_insufficient_heel_lift_marked_invalid() {
        var t = feedFlat()

        // Rise only to 114° (below minimum threshold of 120°)
        val riseAngles = doubleArrayOf(105.0, 112.0, 114.0, 114.0)
        for (angle in riseAngles) {
            t += 200
            fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
        }

        // Lower back to floor
        val lowerAngles = doubleArrayOf(110.0, 104.0, 96.0)
        var repRecorded = false
        for (angle in lowerAngles) {
            t += 200
            val res = fsm.update(emptyMap(), angle, t, EvidenceStatus.Sufficient)
            if (res.completedRep != null) {
                repRecorded = true
                assertFalse("Insufficient heel lift must be marked invalid", res.completedRep!!.isValid)
                assertTrue(res.completedRep!!.failureReasons.any { it.contains("Insufficient heel lift") })
            }
        }

        assertTrue(repRecorded)
        assertEquals(1, fsm.repCounter)
        assertEquals(0, fsm.validRepCounter)
    }
}
