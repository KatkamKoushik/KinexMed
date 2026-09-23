package com.kinexmed.domain.caregiver

import android.content.Context
import android.content.Intent
import com.kinexmed.domain.model.SessionSummary
import com.kinexmed.domain.registry.ExerciseRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper generating structured, privacy-safe rehabilitation summaries for family and caregivers.
 * Strictly transmits factual numbers and encouraging status; zero raw video or private camera images.
 */
object CaregiverShareHelper {

    /**
     * Formats a single session summary for sharing with family or caregivers.
     */
    fun formatSessionShareText(summary: SessionSummary): String {
        val exerciseDef = ExerciseRegistry.getById(summary.exerciseName)
        val sdf = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
        val formattedDate = sdf.format(Date(summary.startedAt))

        val minutes = summary.durationSeconds.toInt() / 60
        val seconds = summary.durationSeconds.toInt() % 60
        val durationFormatted = String.format("%02d:%02d", minutes, seconds)

        val rejectedCount = (summary.totalReps - summary.validReps).coerceAtLeast(0)

        // Find primary rejection reasons if any
        val rejectionReasons = summary.reps
            .filter { !it.isValid }
            .flatMap { it.failureReasons.ifEmpty { listOf(it.feedbackMessage) } }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { "${it.key} (${it.value})" }

        val primaryReasonText = if (rejectionReasons.isNotEmpty()) {
            rejectionReasons.take(2).joinToString(", ")
        } else if (rejectedCount > 0) {
            "Range of motion threshold"
        } else {
            "None - All repetitions completed with target range"
        }

        val adherencePercent = if (summary.totalReps > 0) {
            (summary.validReps * 100) / summary.totalReps
        } else {
            0
        }

        val encouragement = when {
            summary.validReps >= 10 -> "Outstanding effort on today's physical rehabilitation session!"
            summary.validReps >= 5 -> "Great progress staying consistent with home recovery!"
            summary.validReps > 0 -> "Good start today. Every valid repetition builds mobility."
            else -> "Completed session attempt. Focusing on form for upcoming sessions."
        }

        return """
KinexMed Rehabilitation Summary

Exercise: ${exerciseDef.displayName}
Date: $formattedDate
Valid Repetitions: ${summary.validReps} / ${summary.totalReps} ($adherencePercent% quality)
Active Duration: $durationFormatted
Rejected Repetitions: $rejectedCount
Primary Form Notes: $primaryReasonText

Status: $encouragement

Observed on-device via KinexMed Phone Assistant.
        """.trimIndent()
    }

    /**
     * Launches the standard Android system share sheet with the formatted summary text.
     */
    fun shareSessionSummary(context: Context, summary: SessionSummary) {
        val shareText = formatSessionShareText(summary)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "KinexMed Rehabilitation Summary — ${summary.exerciseName}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Rehabilitation Summary")
        context.startActivity(shareIntent)
    }
}
