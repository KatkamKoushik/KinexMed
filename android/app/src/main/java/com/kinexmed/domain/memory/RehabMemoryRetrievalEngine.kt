package com.kinexmed.domain.memory

import com.kinexmed.data.dao.ExerciseFrequencyStat
import com.kinexmed.data.dao.SessionAggregateStats
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.data.repository.SessionRepository
import com.kinexmed.domain.model.ExerciseType
import com.kinexmed.domain.registry.ExerciseRegistry
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RetrievedEvidenceContext(
    val query: ParsedRehabQuery,
    val isMedicalSafetyViolation: Boolean = false,
    val targetRep: RepEntity? = null,
    val targetSession: SessionEntity? = null,
    val rejectedReps: List<RepEntity> = emptyList(),
    val aggregateStats: SessionAggregateStats? = null,
    val exerciseFrequencies: List<ExerciseFrequencyStat> = emptyList(),
    val evidenceCards: List<StructuredEvidenceCard> = emptyList(),
    val summarySnippet: String = ""
)

class RehabMemoryRetrievalEngine(
    private val sessionRepository: SessionRepository
) {

    suspend fun retrieve(parsedQuery: ParsedRehabQuery): RetrievedEvidenceContext {
        if (parsedQuery.intent == QueryIntent.SAFETY_MEDICAL_QUERY) {
            return RetrievedEvidenceContext(
                query = parsedQuery,
                isMedicalSafetyViolation = true,
                summarySnippet = "Clinical safety restriction triggered: Medical diagnoses, drug prescriptions, and clinical evaluations must be referred to licensed medical practitioners."
            )
        }

        return when (parsedQuery.intent) {
            QueryIntent.REP_INSPECTION -> retrieveRepInspection(parsedQuery)
            QueryIntent.REJECTED_REPS_LIST -> retrieveRejectedReps(parsedQuery)
            QueryIntent.RANGE_AGGREGATION -> retrieveRangeAggregation(parsedQuery)
            QueryIntent.EXERCISE_FREQUENCY -> retrieveExerciseFrequency(parsedQuery)
            QueryIntent.SESSION_BREAKDOWN -> retrieveSessionBreakdown(parsedQuery)
            else -> retrieveGeneralStatus(parsedQuery)
        }
    }

    private suspend fun retrieveRepInspection(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val repNum = query.repNumber ?: 1
        var rep = if (query.startTimeMs > 0L) {
            sessionRepository.getRepByNumberAndDate(repNum, query.startTimeMs, query.endTimeMs)
        } else {
            sessionRepository.getLatestRepByNumber(repNum)
        }

        if (rep == null) {
            rep = sessionRepository.getLatestRepByNumber(repNum)
        }

        if (rep == null) {
            return RetrievedEvidenceContext(
                query = query,
                summarySnippet = "No record found for repetition #$repNum in the requested timeframe."
            )
        }

        val session = sessionRepository.getSessionById(rep.sessionId)
        val exerciseName = session?.exerciseName ?: rep.exerciseName.ifEmpty { "Exercise" }
        val targetAngle = resolveTargetAngle(exerciseName)
        val reasons = parseFailureReasons(rep.failureReasonsJson)
        val reasonStr = if (rep.isValid) "Criteria fully satisfied" else reasons.joinToString("; ").ifEmpty { rep.feedbackMessage }

        val card = StructuredEvidenceCard(
            sessionId = rep.sessionId,
            exerciseName = formatExerciseName(exerciseName),
            repNumber = rep.repNumber,
            isValid = rep.isValid,
            measuredAngle = rep.peakKneeAngle,
            targetAngle = targetAngle,
            unit = "°",
            rejectionReason = reasonStr,
            timestamp = rep.endTimestampMs,
            evidenceImagePath = rep.evidenceImagePath,
            videoRecordingPath = session?.videoRecordingPath,
            videoTimestampMs = rep.videoTimestampMs
        )

        return RetrievedEvidenceContext(
            query = query,
            targetRep = rep,
            targetSession = session,
            evidenceCards = listOf(card),
            summarySnippet = "Retrieved Rep #$repNum: ${if (rep.isValid) "VALID" else "REJECTED"} (Measured ${rep.peakKneeAngle.toInt()}°, target ${targetAngle.toInt()}°)"
        )
    }

    private suspend fun retrieveRejectedReps(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val rejectedList = sessionRepository.getRejectedRepsBetween(query.startTimeMs, query.endTimeMs)
        val cards = mutableListOf<StructuredEvidenceCard>()

        for (rep in rejectedList.take(10)) {
            val session = sessionRepository.getSessionById(rep.sessionId)
            val exerciseName = session?.exerciseName ?: rep.exerciseName.ifEmpty { "Exercise" }
            val targetAngle = resolveTargetAngle(exerciseName)
            val reasons = parseFailureReasons(rep.failureReasonsJson)
            val reasonStr = reasons.joinToString("; ").ifEmpty { rep.feedbackMessage }

            cards.add(
                StructuredEvidenceCard(
                    sessionId = rep.sessionId,
                    exerciseName = formatExerciseName(exerciseName),
                    repNumber = rep.repNumber,
                    isValid = false,
                    measuredAngle = rep.peakKneeAngle,
                    targetAngle = targetAngle,
                    unit = "°",
                    rejectionReason = reasonStr,
                    timestamp = rep.endTimestampMs,
                    evidenceImagePath = rep.evidenceImagePath,
                    videoRecordingPath = session?.videoRecordingPath,
                    videoTimestampMs = rep.videoTimestampMs
                )
            )
        }

        return RetrievedEvidenceContext(
            query = query,
            rejectedReps = rejectedList,
            evidenceCards = cards,
            summarySnippet = "Retrieved ${rejectedList.size} rejected repetitions."
        )
    }

    private suspend fun retrieveRangeAggregation(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val stats = sessionRepository.getAggregatedStats(query.startTimeMs, query.endTimeMs)
        val topExercises = sessionRepository.getExerciseFrequencies(query.startTimeMs, query.endTimeMs)

        return RetrievedEvidenceContext(
            query = query,
            aggregateStats = stats,
            exerciseFrequencies = topExercises,
            summarySnippet = "Aggregated: ${stats.totalSessions} sessions, ${stats.totalValidReps} valid reps out of ${stats.totalReps} total."
        )
    }

    private suspend fun retrieveExerciseFrequency(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val freqs = if (query.startTimeMs > 0L) {
            sessionRepository.getExerciseFrequencies(query.startTimeMs, query.endTimeMs)
        } else {
            sessionRepository.getAllTimeExerciseFrequencies()
        }

        return RetrievedEvidenceContext(
            query = query,
            exerciseFrequencies = freqs,
            summarySnippet = if (freqs.isNotEmpty()) "Top exercise: ${freqs.first().exerciseName} (${freqs.first().totalValidReps} valid reps)" else "No exercise records found."
        )
    }

    private suspend fun retrieveSessionBreakdown(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val session = if (query.startTimeMs > 0L) {
            val sessions = sessionRepository.getSessionsBetween(query.startTimeMs, query.endTimeMs)
            sessions.firstOrNull() ?: sessionRepository.getMostRecentSession()
        } else {
            sessionRepository.getMostRecentSession()
        }

        if (session == null) {
            return RetrievedEvidenceContext(
                query = query,
                summarySnippet = "No rehabilitation sessions recorded yet."
            )
        }

        val reps = sessionRepository.getRepsForSessionSync(session.id)
        val rejectedCards = reps.filter { !it.isValid }.map { rep ->
            val targetAngle = resolveTargetAngle(session.exerciseName)
            val reasons = parseFailureReasons(rep.failureReasonsJson)
            StructuredEvidenceCard(
                sessionId = rep.sessionId,
                exerciseName = formatExerciseName(session.exerciseName),
                repNumber = rep.repNumber,
                isValid = false,
                measuredAngle = rep.peakKneeAngle,
                targetAngle = targetAngle,
                unit = "°",
                rejectionReason = reasons.joinToString("; ").ifEmpty { rep.feedbackMessage },
                timestamp = rep.endTimestampMs,
                evidenceImagePath = rep.evidenceImagePath,
                videoRecordingPath = session.videoRecordingPath,
                videoTimestampMs = rep.videoTimestampMs
            )
        }

        return RetrievedEvidenceContext(
            query = query,
            targetSession = session,
            evidenceCards = rejectedCards,
            summarySnippet = "Session ${session.id.take(8)} (${formatExerciseName(session.exerciseName)}): ${session.validReps}/${session.totalReps} valid reps."
        )
    }

    private suspend fun retrieveGeneralStatus(query: ParsedRehabQuery): RetrievedEvidenceContext {
        val latestSession = sessionRepository.getMostRecentSession()
        val allTimeStats = sessionRepository.getAggregatedStats(0L, System.currentTimeMillis())

        return RetrievedEvidenceContext(
            query = query,
            targetSession = latestSession,
            aggregateStats = allTimeStats,
            summarySnippet = "Total sessions: ${allTimeStats.totalSessions}, Total valid reps: ${allTimeStats.totalValidReps}"
        )
    }

    private fun resolveTargetAngle(exerciseName: String): Double {
        val type = ExerciseType.fromId(exerciseName)
        val def = ExerciseRegistry.getByType(type)
        val digits = def.targetMetricTarget.filter { c: Char -> c.isDigit() || c == '.' }
        return digits.toDoubleOrNull() ?: 90.0
    }

    private fun parseFailureReasons(json: String): List<String> {
        val list = mutableListOf<String>()
        if (json.isBlank()) return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun formatExerciseName(name: String): String {
        return when (name.lowercase(Locale.ROOT)) {
            "squat" -> "Bilateral Squat"
            "sit_to_stand" -> "Sit-to-Stand"
            else -> name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}
