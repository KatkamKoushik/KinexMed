package com.kinexmed.domain.controller

import com.kinexmed.domain.evidence.EvidenceEngine
import com.kinexmed.domain.filter.OneEuroFilter
import com.kinexmed.domain.fsm.ExerciseStateMachine
import com.kinexmed.domain.fsm.FsmUpdateResult
import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.*
import com.kinexmed.domain.registry.ExerciseRegistry

/**
 * Controller encapsulating pose biomechanics, evidence engine gating,
 * temporal One-Euro angle smoothing, and finite-state-machine evaluation.
 * Decouples kinematic processing logic from Activity UI lifecycle.
 */
class PoseAnalysisController(
    val exerciseDef: ExerciseDefinition,
    val exerciseFsm: ExerciseStateMachine,
    private val evidenceEngine: EvidenceEngine = EvidenceEngine(),
    private val angleFilter: OneEuroFilter = OneEuroFilter(minCutoff = 1.2, beta = 0.05)
) {
    var minAngleSeenOverall = 180.0
        private set
    var maxAngleSeenOverall = 0.0
        private set
    var lastKnownAngle = 180.0
        private set

    val evidenceEpisodes = mutableListOf<EvidenceEpisode>()
    var currentEvidenceEpisode: EvidenceEpisode? = null
        private set

    fun reset() {
        GeometryEngine.resetSidePreferences()
        minAngleSeenOverall = 180.0
        maxAngleSeenOverall = 0.0
        lastKnownAngle = 180.0
        evidenceEpisodes.clear()
        currentEvidenceEpisode = null

        exerciseFsm.reset()
        evidenceEngine.reset()
        angleFilter.reset()
    }

    fun evaluateEvidenceOnly(
        landmarks: Map<Int, Landmark>,
        timestamp: Long
    ): EvidenceStatus {
        return evidenceEngine.evaluateEvidence(landmarks, timestamp, exerciseDef.type)
    }

    fun processFrame(
        landmarks: Map<Int, Landmark>,
        timestamp: Long
    ): FrameAnalysisResult {
        val evidenceStatus = evidenceEngine.evaluateEvidence(landmarks, timestamp, exerciseDef.type)

        if (evidenceStatus is EvidenceStatus.Insufficient) {
            val failureReason = evidenceStatus.reason
            val episode = currentEvidenceEpisode
            if (episode != null && episode.reason == failureReason && (timestamp - episode.endTimeMs) < 1000L) {
                episode.endTimeMs = timestamp
                episode.frameCount++
            } else {
                if (episode != null) {
                    evidenceEpisodes.add(episode)
                }
                currentEvidenceEpisode = EvidenceEpisode(
                    reason = failureReason,
                    startTimeMs = timestamp,
                    endTimeMs = timestamp,
                    frameCount = 1
                )
            }

            // Update FSM with insufficient evidence so grace period is maintained
            val fsmResult = exerciseFsm.update(landmarks, lastKnownAngle, timestamp, evidenceStatus)
            return FrameAnalysisResult(
                evidenceStatus = evidenceStatus,
                smoothedAngle = null,
                fsmResult = fsmResult,
                completedRep = fsmResult.completedRep
            )
        }

        // Evidence is sufficient: close active episode if any
        if (currentEvidenceEpisode != null) {
            evidenceEpisodes.add(currentEvidenceEpisode!!)
            currentEvidenceEpisode = null
        }

        val rawAngle = ExerciseRegistry.extractMetric(exerciseDef.type, landmarks, timestamp)
        if (rawAngle == null) {
            val fsmResult = exerciseFsm.update(landmarks, lastKnownAngle, timestamp, evidenceStatus)
            return FrameAnalysisResult(
                evidenceStatus = evidenceStatus,
                smoothedAngle = null,
                fsmResult = fsmResult,
                completedRep = null
            )
        }

        val smoothedAngle = angleFilter.filter(rawAngle.angleDegrees, timestamp)
        lastKnownAngle = smoothedAngle

        if (smoothedAngle < minAngleSeenOverall) minAngleSeenOverall = smoothedAngle
        if (smoothedAngle > maxAngleSeenOverall) maxAngleSeenOverall = smoothedAngle

        val fsmResult = exerciseFsm.update(landmarks, smoothedAngle, timestamp, evidenceStatus)

        return FrameAnalysisResult(
            evidenceStatus = evidenceStatus,
            smoothedAngle = smoothedAngle,
            fsmResult = fsmResult,
            completedRep = fsmResult.completedRep
        )
    }

    fun finalizeEpisodes() {
        if (currentEvidenceEpisode != null) {
            evidenceEpisodes.add(currentEvidenceEpisode!!)
            currentEvidenceEpisode = null
        }
    }
}

data class FrameAnalysisResult(
    val evidenceStatus: EvidenceStatus,
    val smoothedAngle: Double?,
    val fsmResult: FsmUpdateResult,
    val completedRep: RepRecord?
)
