package com.kinexmed

import com.kinexmed.data.dao.ExerciseFrequencyStat
import com.kinexmed.data.dao.SessionAggregateStats
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.memory.DeterministicMemoryExplanationEngine
import com.kinexmed.domain.memory.ParsedRehabQuery
import com.kinexmed.domain.memory.QueryIntent
import com.kinexmed.domain.memory.RetrievedEvidenceContext
import com.kinexmed.domain.memory.StructuredEvidenceCard
import com.kinexmed.domain.memory.TimeRangeType
import org.junit.Assert.*
import org.junit.Test

class DeterministicMemoryExplanationEngineTest {

    @Test
    fun testMedicalSafetyRejectionNeverDiagnoses() {
        val query = ParsedRehabQuery(
            intent = QueryIntent.SAFETY_MEDICAL_QUERY,
            timeRangeType = TimeRangeType.TODAY,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            rawQuery = "Can you diagnose my knee?"
        )
        val context = RetrievedEvidenceContext(
            query = query,
            isMedicalSafetyViolation = true
        )

        val (explanation, cards) = DeterministicMemoryExplanationEngine.generateExplanation(context)
        assertTrue(explanation.contains("cannot provide clinical diagnoses"))
        assertTrue(explanation.contains("physician or licensed physical therapist"))
        assertTrue(cards.isEmpty())
    }

    @Test
    fun testRepInspectionRejectedProvidesAngleAndTarget() {
        val query = ParsedRehabQuery(
            intent = QueryIntent.REP_INSPECTION,
            timeRangeType = TimeRangeType.YESTERDAY,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            repNumber = 7,
            rawQuery = "Why wasn't my 7th rep counted yesterday?"
        )
        val rep = RepEntity(
            sessionId = "session-123",
            repNumber = 7,
            isValid = false,
            startTimestampMs = 100L,
            peakTimestampMs = 200L,
            endTimestampMs = 300L,
            durationMs = 200L,
            peakKneeAngle = 68.0,
            startKneeAngle = 175.0,
            endKneeAngle = 175.0,
            feedbackMessage = "Insufficient flexion depth",
            failureReasonsJson = "[\"Insufficient flexion depth\"]"
        )
        val card = StructuredEvidenceCard(
            sessionId = "session-123",
            exerciseName = "Bilateral Squat",
            repNumber = 7,
            isValid = false,
            measuredAngle = 68.0,
            targetAngle = 90.0,
            rejectionReason = "Insufficient flexion depth",
            timestamp = 300L
        )
        val context = RetrievedEvidenceContext(
            query = query,
            targetRep = rep,
            evidenceCards = listOf(card)
        )

        val (explanation, cards) = DeterministicMemoryExplanationEngine.generateExplanation(context)
        assertTrue(explanation.contains("Repetition #7 was not counted"))
        assertTrue(explanation.contains("68°"))
        assertTrue(explanation.contains("90°"))
        assertEquals(1, cards.size)
        assertEquals(68.0, cards[0].measuredAngle, 0.01)
    }

    @Test
    fun testRangeAggregationComputesAccurateAdherence() {
        val query = ParsedRehabQuery(
            intent = QueryIntent.RANGE_AGGREGATION,
            timeRangeType = TimeRangeType.THIS_WEEK,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            rawQuery = "How many valid repetitions did I complete this week?"
        )
        val stats = SessionAggregateStats(
            totalSessions = 5,
            totalValidReps = 42,
            totalReps = 50,
            avgRom = 94.2,
            totalDurationSeconds = 600.0
        )
        val context = RetrievedEvidenceContext(
            query = query,
            aggregateStats = stats
        )

        val (explanation, _) = DeterministicMemoryExplanationEngine.generateExplanation(context)
        assertTrue(explanation.contains("42 valid repetitions"))
        assertTrue(explanation.contains("50 total"))
        assertTrue(explanation.contains("5 session(s)"))
        assertTrue(explanation.contains("84% compliance"))
    }

    @Test
    fun testExerciseFrequencyReportsTopExercise() {
        val query = ParsedRehabQuery(
            intent = QueryIntent.EXERCISE_FREQUENCY,
            timeRangeType = TimeRangeType.ALL_TIME,
            startTimeMs = 0L,
            endTimeMs = 1000L,
            rawQuery = "Which exercise did I perform most?"
        )
        val frequencies = listOf(
            ExerciseFrequencyStat(exerciseName = "squat", sessionCount = 8, totalValidReps = 72),
            ExerciseFrequencyStat(exerciseName = "sit_to_stand", sessionCount = 3, totalValidReps = 24)
        )
        val context = RetrievedEvidenceContext(
            query = query,
            exerciseFrequencies = frequencies
        )

        val (explanation, _) = DeterministicMemoryExplanationEngine.generateExplanation(context)
        assertTrue(explanation.contains("Bilateral Squat"))
        assertTrue(explanation.contains("8 session(s)"))
        assertTrue(explanation.contains("72 valid repetitions"))
    }
}
