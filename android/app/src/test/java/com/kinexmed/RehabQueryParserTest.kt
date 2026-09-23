package com.kinexmed

import com.kinexmed.domain.memory.QueryIntent
import com.kinexmed.domain.memory.RehabQueryParser
import com.kinexmed.domain.memory.TimeRangeType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RehabQueryParserTest {

    private val fixedNowMs = 1790100000000L // arbitrary fixed epoch timestamp for tests

    @Test
    fun testWhyWasnt7thRepCountedYesterday() {
        val parsed = RehabQueryParser.parse("Why wasn't my 7th rep counted yesterday?", fixedNowMs)
        assertEquals(QueryIntent.REP_INSPECTION, parsed.intent)
        assertEquals(7, parsed.repNumber)
        assertEquals(TimeRangeType.YESTERDAY, parsed.timeRangeType)
        assertTrue(parsed.startTimeMs > 0L)
        assertTrue(parsed.endTimeMs > parsed.startTimeMs)
    }

    @Test
    fun testShowRejectedRepsFromSeptember20() {
        val parsed = RehabQueryParser.parse("Show me my rejected reps from September 20.", fixedNowMs)
        assertEquals(QueryIntent.REJECTED_REPS_LIST, parsed.intent)
        assertEquals(TimeRangeType.SPECIFIC_DATE, parsed.timeRangeType)
        assertTrue(parsed.startTimeMs > 0L)
        assertTrue(parsed.endTimeMs > parsed.startTimeMs)
    }

    @Test
    fun testHowManyValidRepsThisWeek() {
        val parsed = RehabQueryParser.parse("How many valid repetitions did I complete this week?", fixedNowMs)
        assertEquals(QueryIntent.RANGE_AGGREGATION, parsed.intent)
        assertEquals(TimeRangeType.THIS_WEEK, parsed.timeRangeType)
    }

    @Test
    fun testSummaryOfThisMonth() {
        val parsed = RehabQueryParser.parse("Give me a summary of this month.", fixedNowMs)
        assertEquals(QueryIntent.RANGE_AGGREGATION, parsed.intent)
        assertEquals(TimeRangeType.THIS_MONTH, parsed.timeRangeType)
    }

    @Test
    fun testWhichExercisePerformedMost() {
        val parsed = RehabQueryParser.parse("Which exercise did I perform most?", fixedNowMs)
        assertEquals(QueryIntent.EXERCISE_FREQUENCY, parsed.intent)
    }

    @Test
    fun testExplainTodaySessionSimply() {
        val parsed = RehabQueryParser.parse("Explain today's session simply.", fixedNowMs)
        assertEquals(QueryIntent.SESSION_BREAKDOWN, parsed.intent)
        assertEquals(TimeRangeType.TODAY, parsed.timeRangeType)
    }

    @Test
    fun testWhatHappenedDuringLastSession() {
        val parsed = RehabQueryParser.parse("What happened during my last session?", fixedNowMs)
        assertEquals(QueryIntent.SESSION_BREAKDOWN, parsed.intent)
    }

    @Test
    fun testMedicalSafetyGuardrailInterceptsDiagnosis() {
        val parsed = RehabQueryParser.parse("Can you diagnose if my ACL is torn?", fixedNowMs)
        assertEquals(QueryIntent.SAFETY_MEDICAL_QUERY, parsed.intent)
    }

    @Test
    fun testMedicalSafetyGuardrailInterceptsPrescription() {
        val parsed = RehabQueryParser.parse("What medication should I take for this pain?", fixedNowMs)
        assertEquals(QueryIntent.SAFETY_MEDICAL_QUERY, parsed.intent)
    }
}
