package com.kinexmed

import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.model.SessionSummary
import com.kinexmed.domain.report.ReportGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportGeneratorTest {

    @Test
    fun testGenerateSessionReport_containsGuardrailAndFactualData() {
        val summary = SessionSummary(
            sessionId = "sess-12345",
            exerciseName = "sit_to_stand",
            startedAt = 1700000000000L,
            completedAt = 1700000060000L,
            durationSeconds = 60.0,
            totalReps = 3,
            validReps = 2,
            avgPeakKneeAngle = 151.0,
            minKneeAngle = 88.0,
            maxKneeAngle = 155.0,
            evidenceFailureCount = 0,
            reps = listOf(
                RepRecord(1, true, 1000, 2000, 3000, 2000, 152.0, 90.0, 90.0, "Good rep"),
                RepRecord(2, true, 3000, 4000, 5000, 2000, 150.0, 90.0, 90.0, "Good rep"),
                RepRecord(3, false, 5000, 6000, 7000, 2000, 138.0, 90.0, 90.0, "Incomplete extension", listOf("Incomplete extension (<148°)"))
            )
        )

        val report = ReportGenerator.generateSessionReport(summary)

        assertTrue(report.contains("KINEXMED CLINICAL SESSION REPORT"))
        assertTrue(report.contains("Sit-to-Stand"))
        assertTrue(report.contains("Valid Repetitions:    2"))
        assertTrue(report.contains("Total Attempted:      3"))
        assertTrue(report.contains("Incomplete extension (<148°)"))
        assertTrue(report.contains("non-diagnostic"))
    }
}
