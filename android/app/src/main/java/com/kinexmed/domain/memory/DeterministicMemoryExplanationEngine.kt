package com.kinexmed.domain.memory

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeterministicMemoryExplanationEngine {

    fun generateExplanation(context: RetrievedEvidenceContext): Pair<String, List<StructuredEvidenceCard>> {
        // 1. Strict Medical Safety & Diagnostic Guardrail
        if (context.isMedicalSafetyViolation) {
            val safetyMessage = "As a rehabilitation assistant, I cannot provide clinical diagnoses, prescribe medication, or evaluate medical pathology. Please consult your physician or licensed physical therapist for clinical medical decisions."
            return Pair(safetyMessage, emptyList())
        }

        val cards = context.evidenceCards

        val responseText = when (context.query.intent) {
            QueryIntent.REP_INSPECTION -> explainRepInspection(context)
            QueryIntent.REJECTED_REPS_LIST -> explainRejectedReps(context)
            QueryIntent.RANGE_AGGREGATION -> explainRangeAggregation(context)
            QueryIntent.EXERCISE_FREQUENCY -> explainExerciseFrequency(context)
            QueryIntent.SESSION_BREAKDOWN -> explainSessionBreakdown(context)
            QueryIntent.GENERAL_STATUS -> explainGeneralStatus(context)
            QueryIntent.SAFETY_MEDICAL_QUERY -> "Please consult your licensed physical therapist or physician for clinical advice."
        }

        return Pair(responseText, cards)
    }

    private fun explainRepInspection(context: RetrievedEvidenceContext): String {
        val rep = context.targetRep ?: return "I could not find a repetition matching #${context.query.repNumber ?: 1} in your recorded sessions. Please check the rep number or try another query."
        val card = context.evidenceCards.firstOrNull()
        val target = card?.targetAngle ?: 90.0
        val measured = rep.peakKneeAngle.toInt()
        val repNum = rep.repNumber

        return if (rep.isValid) {
            "Repetition #$repNum was successfully counted as VALID. Your measured angle reached ${measured}°, fully satisfying the required ${target.toInt()}° target threshold."
        } else {
            val reason = card?.rejectionReason ?: rep.feedbackMessage.ifEmpty { "movement criteria not met" }
            "Repetition #$repNum was not counted because $reason. Your measured peak angle was ${measured}°, compared to the configured target of ${target.toInt()}°. Tap the evidence card below to inspect the measured telemetry."
        }
    }

    private fun explainRejectedReps(context: RetrievedEvidenceContext): String {
        val count = context.rejectedReps.size
        if (count == 0) {
            return "Great news: No rejected repetitions were found for this timeframe. All attempted repetitions met the target criteria!"
        }

        val sampleCard = context.evidenceCards.firstOrNull()
        val reasonSnippet = if (sampleCard != null) "Common reason: ${sampleCard.rejectionReason}." else ""

        return "Found $count rejected repetition(s) in this period. $reasonSnippet Review the structured evidence cards below to inspect each movement."
    }

    private fun explainRangeAggregation(context: RetrievedEvidenceContext): String {
        val stats = context.aggregateStats ?: return "No session telemetry recorded for this timeframe yet."
        if (stats.totalSessions == 0) {
            return "No rehabilitation sessions have been completed in this period."
        }

        val adherence = if (stats.totalReps > 0) ((stats.totalValidReps.toDouble() / stats.totalReps) * 100).toInt() else 0
        val activeMinutes = (stats.totalDurationSeconds / 60).toInt()
        val periodName = when (context.query.timeRangeType) {
            TimeRangeType.THIS_WEEK -> "this week"
            TimeRangeType.THIS_MONTH -> "this month"
            TimeRangeType.TODAY -> "today"
            TimeRangeType.YESTERDAY -> "yesterday"
            else -> "over this period"
        }

        return "Summary for $periodName: You completed ${stats.totalValidReps} valid repetitions out of ${stats.totalReps} total across ${stats.totalSessions} session(s) (${adherence}% compliance). Total active rehabilitation time was ${activeMinutes} minute(s) with an average ROM of ${stats.avgRom.toInt()}°."
    }

    private fun explainExerciseFrequency(context: RetrievedEvidenceContext): String {
        val freqs = context.exerciseFrequencies
        if (freqs.isEmpty()) {
            return "No exercise sessions have been recorded yet."
        }

        val top = freqs.first()
        val topName = formatExerciseName(top.exerciseName)
        val others = freqs.drop(1).take(2)
        val breakdown = if (others.isNotEmpty()) {
            " Followed by " + others.joinToString(", ") { "${formatExerciseName(it.exerciseName)} (${it.totalValidReps} reps)" } + "."
        } else ""

        return "Your most performed exercise is $topName with ${top.sessionCount} session(s) and ${top.totalValidReps} valid repetitions completed.$breakdown"
    }

    private fun explainSessionBreakdown(context: RetrievedEvidenceContext): String {
        val session = context.targetSession ?: return "No recorded sessions found to explain. Complete your first session to begin tracking!"
        val exerciseName = formatExerciseName(session.exerciseName)
        val adherence = if (session.totalReps > 0) ((session.validReps.toDouble() / session.totalReps) * 100).toInt() else 0
        val durationMin = session.durationSeconds.toInt() / 60
        val durationSec = session.durationSeconds.toInt() % 60
        val rejectedCount = session.totalReps - session.validReps

        val rejectedSnippet = if (rejectedCount > 0) {
            " $rejectedCount repetition(s) did not meet target ROM. See the cards below for details."
        } else {
            " All attempted repetitions met the target form criteria!"
        }

        return "In your session of $exerciseName, you completed ${session.validReps} of ${session.totalReps} valid repetitions (${adherence}% completion) in ${String.format(Locale.ROOT, "%02d:%02d", durationMin, durationSec)} with an average peak angle of ${session.avgPeakKneeAngle.toInt()}°.$rejectedSnippet"
    }

    private fun explainGeneralStatus(context: RetrievedEvidenceContext): String {
        val stats = context.aggregateStats
        val latest = context.targetSession

        return if (latest != null) {
            "Your latest recorded session was ${formatExerciseName(latest.exerciseName)} with ${latest.validReps} valid reps. Overall, you have completed ${stats?.totalValidReps ?: latest.validReps} valid reps across ${stats?.totalSessions ?: 1} sessions."
        } else {
            "Welcome to KinexMed Personal Rehabilitation Assistant. Ask me about your repetitions, past sessions, rejected movements, or weekly summaries!"
        }
    }

    private fun formatExerciseName(name: String): String {
        return when (name.lowercase(Locale.ROOT)) {
            "squat" -> "Bilateral Squat"
            "sit_to_stand" -> "Sit-to-Stand"
            else -> name.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }
}
