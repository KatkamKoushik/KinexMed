package com.kinexmed.domain.consistency

import com.kinexmed.data.entity.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WeeklyProgressStat(
    val completedSessions: Int,
    val targetSessions: Int,
    val validReps: Int,
    val attemptedReps: Int,
    val adherencePercent: Double,
    val totalDurationSeconds: Double,
    val exerciseDistribution: Map<String, Int>
)

data class MonthlyProgressStat(
    val completedSessions: Int,
    val validReps: Int,
    val attemptedReps: Int,
    val adherencePercent: Double,
    val totalDurationSeconds: Double,
    val exerciseDistribution: Map<String, Int>
)

/**
 * Pure deterministic calculation engine for streaks and progress aggregations.
 * Strictly derives metrics from persistent Room SQLite session records.
 * Zero hardcoded values or mock data.
 */
object ConsistencyCalculator {

    private fun getDayString(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(timestampMs))
    }

    /**
     * Calculates the active consecutive day streak ending today or yesterday.
     * Returns 0 if there are no recorded sessions or if the last session was before yesterday.
     */
    fun calculateCurrentStreak(
        sessions: List<SessionEntity>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (sessions.isEmpty()) return 0

        val distinctDays = sessions.map { getDayString(it.startedAt, timeZone) }.toSet()

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayStr = getDayString(cal.timeInMillis, timeZone)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = getDayString(cal.timeInMillis, timeZone)

        // Streak anchor must be today or yesterday
        val anchorCal = if (distinctDays.contains(todayStr)) {
            Calendar.getInstance(timeZone).apply { timeInMillis = nowMs }
        } else if (distinctDays.contains(yesterdayStr)) {
            Calendar.getInstance(timeZone).apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -1)
            }
        } else {
            return 0
        }

        var streak = 0
        while (true) {
            val dayStr = getDayString(anchorCal.timeInMillis, timeZone)
            if (distinctDays.contains(dayStr)) {
                streak++
                anchorCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Calculates the longest all-time streak of consecutive active days.
     */
    fun calculateLongestStreak(
        sessions: List<SessionEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        if (sessions.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }

        val sortedDays = sessions
            .map { getDayString(it.startedAt, timeZone) }
            .distinct()
            .sorted()

        if (sortedDays.isEmpty()) return 0

        var maxStreak = 1
        var currentStreak = 1

        val calPrev = Calendar.getInstance(timeZone)
        val calCurr = Calendar.getInstance(timeZone)

        for (i in 1 until sortedDays.size) {
            val prevDate = sdf.parse(sortedDays[i - 1]) ?: continue
            val currDate = sdf.parse(sortedDays[i]) ?: continue

            calPrev.time = prevDate
            calCurr.time = currDate

            calPrev.add(Calendar.DAY_OF_YEAR, 1)

            if (calPrev.get(Calendar.YEAR) == calCurr.get(Calendar.YEAR) &&
                calPrev.get(Calendar.DAY_OF_YEAR) == calCurr.get(Calendar.DAY_OF_YEAR)
            ) {
                currentStreak++
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak
                }
            } else {
                currentStreak = 1
            }
        }

        return maxStreak
    }

    /**
     * Aggregates progress metrics for the current calendar week (Monday to Sunday).
     */
    fun calculateWeeklyProgress(
        sessions: List<SessionEntity>,
        nowMs: Long = System.currentTimeMillis(),
        targetSessions: Int = 5,
        timeZone: TimeZone = TimeZone.getDefault()
    ): WeeklyProgressStat {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfWeekMs = cal.timeInMillis

        cal.add(Calendar.DAY_OF_WEEK, 7)
        val endOfWeekMs = cal.timeInMillis

        val weekSessions = sessions.filter { it.startedAt in startOfWeekMs until endOfWeekMs }

        val totalAttempted = weekSessions.sumOf { it.totalReps }
        val totalValid = weekSessions.sumOf { it.validReps }
        val duration = weekSessions.sumOf { it.durationSeconds }
        val adherence = if (totalAttempted > 0) (totalValid.toDouble() / totalAttempted) * 100.0 else 0.0

        val distribution = weekSessions.groupBy { it.exerciseName }
            .mapValues { entry -> entry.value.sumOf { it.validReps } }

        return WeeklyProgressStat(
            completedSessions = weekSessions.size,
            targetSessions = targetSessions,
            validReps = totalValid,
            attemptedReps = totalAttempted,
            adherencePercent = adherence,
            totalDurationSeconds = duration,
            exerciseDistribution = distribution
        )
    }

    /**
     * Aggregates progress metrics for the current calendar month.
     */
    fun calculateMonthlyProgress(
        sessions: List<SessionEntity>,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): MonthlyProgressStat {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonthMs = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        val endOfMonthMs = cal.timeInMillis

        val monthSessions = sessions.filter { it.startedAt in startOfMonthMs until endOfMonthMs }

        val totalAttempted = monthSessions.sumOf { it.totalReps }
        val totalValid = monthSessions.sumOf { it.validReps }
        val duration = monthSessions.sumOf { it.durationSeconds }
        val adherence = if (totalAttempted > 0) (totalValid.toDouble() / totalAttempted) * 100.0 else 0.0

        val distribution = monthSessions.groupBy { it.exerciseName }
            .mapValues { entry -> entry.value.sumOf { it.validReps } }

        return MonthlyProgressStat(
            completedSessions = monthSessions.size,
            validReps = totalValid,
            attemptedReps = totalAttempted,
            adherencePercent = adherence,
            totalDurationSeconds = duration,
            exerciseDistribution = distribution
        )
    }
}
