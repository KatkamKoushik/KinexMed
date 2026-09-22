package com.kinexmed.domain.fsm

import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.ExerciseState
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.RepRecord

/**
 * Common finite state machine contract for all rehabilitation exercises.
 * Enables reusable frame tracking, unified rep evaluation, and clean UI observation.
 */
interface ExerciseStateMachine {
    val exerciseType: ExerciseType
    val currentState: ExerciseState
    val repCounter: Int
    val validRepCounter: Int
    val primaryMetricName: String
    val primaryMetricUnit: String
    val targetThresholdDesc: String

    fun reset()

    fun update(
        landmarks: Map<Int, Landmark>,
        smoothedMetric: Double,
        timestampMs: Long,
        evidenceStatus: EvidenceStatus
    ): FsmUpdateResult
}
