package com.kinexmed.domain.report

import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.consistency.ConsistencyCalculator
import com.kinexmed.domain.model.SessionSummary
import com.kinexmed.domain.registry.ExerciseRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Factual clinical report generator derived deterministically from Room SQLite data.
 * Produces structured Session, Weekly, and Monthly Clinical Adherence Reports.
 */
object ReportGenerator {

    /**
     * Generates a formal clinical adherence report for a single session.
     */
    fun generateSessionReport(summary: SessionSummary): String {
        val exerciseDef = ExerciseRegistry.getById(summary.exerciseName)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val startFormatted = sdf.format(Date(summary.startedAt))

        val adherencePercent = if (summary.totalReps > 0) {
            String.format(Locale.US, "%.1f%%", (summary.validReps.toDouble() / summary.totalReps) * 100.0)
        } else {
            "0.0%"
        }

        val rejections = summary.reps.filter { !it.isValid }
        val rejectionBreakdown = rejections
            .flatMap { it.failureReasons.ifEmpty { listOf(it.feedbackMessage) } }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .joinToString("\n") { "  - ${it.key}: ${it.value} occurrences" }
            .ifEmpty { "  - None (All repetitions satisfied joint kinematics)" }

        return """
======================================================
KINEXMED CLINICAL SESSION REPORT (ID: ${summary.sessionId})
======================================================
Exercise:               ${exerciseDef.displayName} (${exerciseDef.category.displayName})
Timestamp:              $startFormatted
Active Duration:        ${String.format(Locale.US, "%.1f", summary.durationSeconds)} seconds
Target Metric:          ${exerciseDef.targetMetricName} (${exerciseDef.targetMetricTarget})

KINEMATIC RESULTS:
  Total Attempted:      ${summary.totalReps}
  Valid Repetitions:    ${summary.validReps}
  Adherence Ratio:      $adherencePercent
  Average Peak ROM:     ${String.format(Locale.US, "%.1f°", summary.avgPeakKneeAngle)}
  Minimum Angle:        ${String.format(Locale.US, "%.1f°", summary.minKneeAngle)}
  Maximum Angle:        ${String.format(Locale.US, "%.1f°", summary.maxKneeAngle)}
  Evidence Alerts:      ${summary.evidenceFailureCount}

FORM & REJECTION ANALYSIS:
$rejectionBreakdown

DEVICE & STORAGE INTEGRITY:
  Storage:              On-Device Room SQLite
  Video Mode:           ${if (summary.isRecordingEnabled) "Local Opt-in MP4" else "Frame buffer released (Privacy default)"}
  Clinical Guardrail:   Assistive kinematic telemetry only; non-diagnostic.
======================================================
        """.trimIndent()
    }

    /**
     * Generates a weekly adherence report across multiple sessions.
     */
    fun generateWeeklyReport(sessions: List<SessionEntity>, targetWeeklySessions: Int = 5): String {
        val stat = ConsistencyCalculator.calculateWeeklyProgress(sessions, targetSessions = targetWeeklySessions)
        val streak = ConsistencyCalculator.calculateCurrentStreak(sessions)

        val exerciseLines = stat.exerciseDistribution.entries
            .joinToString("\n") { (ex, count) ->
                val def = ExerciseRegistry.getById(ex)
                "  - ${def.displayName}: $count valid repetitions"
            }
            .ifEmpty { "  - No exercises recorded this week" }

        val adherenceStr = if (stat.attemptedReps > 0) {
            String.format(Locale.US, "%.1f%%", stat.adherencePercent)
        } else {
            "0.0%"
        }

        val totalMinutes = (stat.totalDurationSeconds / 60).toInt()

        return """
======================================================
KINEXMED WEEKLY CLINICAL ADHERENCE REPORT
======================================================
Reporting Period:       Current Calendar Week
Completed Sessions:     ${stat.completedSessions} / ${stat.targetSessions} (Goal Progress: ${if (stat.targetSessions > 0) (stat.completedSessions * 100) / stat.targetSessions else 0}%)
Current Active Streak:  $streak days

VOLUME & ACCURACY:
  Total Valid Reps:     ${stat.validReps}
  Total Attempted Reps: ${stat.attemptedReps}
  Overall Quality:      $adherenceStr
  Total Active Time:    $totalMinutes minutes

EXERCISE DISTRIBUTION:
$exerciseLines

CLINICAL RECOMMENDATION STATUS:
${if (stat.completedSessions >= stat.targetSessions) "Goal achieved. Patient demonstrates consistent weekly home exercise engagement." else "Below target frequency. Encourage consistent scheduled sessions."}
======================================================
        """.trimIndent()
    }

    /**
     * Generates a monthly rehabilitation progress report.
     */
    fun generateMonthlyReport(sessions: List<SessionEntity>): String {
        val stat = ConsistencyCalculator.calculateMonthlyProgress(sessions)
        val longestStreak = ConsistencyCalculator.calculateLongestStreak(sessions)

        val exerciseLines = stat.exerciseDistribution.entries
            .joinToString("\n") { (ex, count) ->
                val def = ExerciseRegistry.getById(ex)
                "  - ${def.displayName}: $count valid repetitions"
            }
            .ifEmpty { "  - No exercises recorded this month" }

        val adherenceStr = if (stat.attemptedReps > 0) {
            String.format(Locale.US, "%.1f%%", stat.adherencePercent)
        } else {
            "0.0%"
        }

        val totalHours = String.format(Locale.US, "%.1f", stat.totalDurationSeconds / 3600.0)

        return """
======================================================
KINEXMED MONTHLY REHABILITATION SUMMARY
======================================================
Reporting Period:       Current Calendar Month
Total Sessions:         ${stat.completedSessions}
Longest Active Streak:  $longestStreak consecutive days

VOLUME & ACCURACY:
  Total Valid Reps:     ${stat.validReps}
  Total Attempted Reps: ${stat.attemptedReps}
  Monthly Quality:      $adherenceStr
  Total Active Therapy: $totalHours hours

EXERCISE VOLUME BREAKDOWN:
$exerciseLines

DISCLAIMER:
  Data compiled deterministically from on-device sensor kinematics.
  Does not constitute an autonomous medical prescription or treatment diagnosis.
======================================================
        """.trimIndent()
    }
}
