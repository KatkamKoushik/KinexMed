package com.kinexmed

import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.consistency.ConsistencyCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ConsistencyCalculatorTest {

    private val tz = TimeZone.getTimeZone("UTC")

    private fun createSession(dayOffsetFromNow: Int, nowMs: Long, validReps: Int = 10, totalReps: Int = 10): SessionEntity {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, dayOffsetFromNow)
            set(Calendar.HOUR_OF_DAY, 10)
        }
        return SessionEntity(
            id = "sess-$dayOffsetFromNow",
            exerciseName = "sit_to_stand",
            startedAt = cal.timeInMillis,
            completedAt = cal.timeInMillis + 60000,
            durationSeconds = 60.0,
            totalReps = totalReps,
            validReps = validReps,
            avgPeakKneeAngle = 150.0,
            minKneeAngle = 85.0,
            maxKneeAngle = 160.0,
            evidenceFailureCount = 0
        )
    }

    @Test
    fun testEmptySessions_returnsZero() {
        val streak = ConsistencyCalculator.calculateCurrentStreak(emptyList(), timeZone = tz)
        assertEquals(0, streak)

        val longest = ConsistencyCalculator.calculateLongestStreak(emptyList(), timeZone = tz)
        assertEquals(0, longest)
    }

    @Test
    fun testCurrentStreak_withSessionToday() {
        val now = 1700000000000L
        val sessions = listOf(
            createSession(0, now),
            createSession(-1, now),
            createSession(-2, now)
        )
        val streak = ConsistencyCalculator.calculateCurrentStreak(sessions, nowMs = now, timeZone = tz)
        assertEquals(3, streak)
    }

    @Test
    fun testCurrentStreak_withSessionYesterdayOnly() {
        val now = 1700000000000L
        val sessions = listOf(
            createSession(-1, now),
            createSession(-2, now)
        )
        // Today has no session yet, but yesterday did: streak is still alive!
        val streak = ConsistencyCalculator.calculateCurrentStreak(sessions, nowMs = now, timeZone = tz)
        assertEquals(2, streak)
    }

    @Test
    fun testCurrentStreak_brokenStreak_returnsZero() {
        val now = 1700000000000L
        val sessions = listOf(
            createSession(-3, now),
            createSession(-4, now)
        )
        val streak = ConsistencyCalculator.calculateCurrentStreak(sessions, nowMs = now, timeZone = tz)
        assertEquals(0, streak)
    }

    @Test
    fun testLongestStreak() {
        val now = 1700000000000L
        val sessions = listOf(
            // Cluster 1: 2 days
            createSession(-10, now),
            createSession(-9, now),
            // Cluster 2: 4 days
            createSession(-5, now),
            createSession(-4, now),
            createSession(-3, now),
            createSession(-2, now),
            // Cluster 3: 1 day
            createSession(0, now)
        )
        val longest = ConsistencyCalculator.calculateLongestStreak(sessions, timeZone = tz)
        assertEquals(4, longest)
    }

    @Test
    fun testWeeklyProgress() {
        val now = 1700000000000L
        val sessions = listOf(
            createSession(0, now, validReps = 8, totalReps = 10)
        )
        val weekly = ConsistencyCalculator.calculateWeeklyProgress(sessions, nowMs = now, targetSessions = 5, timeZone = tz)
        assertEquals(1, weekly.completedSessions)
        assertEquals(5, weekly.targetSessions)
        assertEquals(8, weekly.validReps)
        assertEquals(10, weekly.attemptedReps)
        assertEquals(80.0, weekly.adherencePercent, 0.01)
    }
}
