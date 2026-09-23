package com.kinexmed

import com.kinexmed.domain.caregiver.CaregiverShareHelper
import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.model.SessionSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaregiverShareHelperTest {

    @Test
    fun testFormatSessionShareText_zeroVideoLeakageAndFactual() {
        val summary = SessionSummary(
            sessionId = "sess-cg-1",
            exerciseName = "squat",
            startedAt = 1700000000000L,
            completedAt = 1700000090000L,
            durationSeconds = 90.0,
            totalReps = 10,
            validReps = 8,
            avgPeakKneeAngle = 92.0,
            minKneeAngle = 90.0,
            maxKneeAngle = 175.0,
            evidenceFailureCount = 0,
            reps = listOf(
                RepRecord(1, true, 1000, 2000, 3000, 2000, 92.0, 175.0, 175.0, "Great depth"),
                RepRecord(2, false, 3000, 4000, 5000, 2000, 110.0, 175.0, 175.0, "Insufficient depth", listOf("Insufficient depth (>100°)"))
            ),
            videoRecordingPath = "/data/user/0/com.kinexmed/files/session_videos/sess-cg-1.mp4",
            isRecordingEnabled = true
        )

        val shareText = CaregiverShareHelper.formatSessionShareText(summary)

        assertTrue(shareText.contains("KinexMed Rehabilitation Summary"))
        assertTrue(shareText.contains("Bilateral Squat"))
        assertTrue(shareText.contains("Valid Repetitions: 8 / 10"))
        assertTrue(shareText.contains("Rejected Repetitions: 2"))
        // Strict privacy guarantee: local MP4 path MUST NOT be in the shared text
        assertFalse(shareText.contains("session_videos"))
        assertFalse(shareText.contains(".mp4"))
    }
}
