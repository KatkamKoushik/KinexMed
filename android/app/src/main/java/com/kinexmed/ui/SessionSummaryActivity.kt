package com.kinexmed.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.domain.caregiver.CaregiverShareHelper
import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.model.SessionSummary
import com.kinexmed.domain.registry.ExerciseRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated patient post-session summary screen.
 * Displays transparent deterministic form scoring, kinematic averages,
 * primary rejection reasons, and quick actions to review reps, ask chat assistant,
 * or share a structured summary with caregivers.
 */
class SessionSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    private var currentSummary: SessionSummary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_summary)

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId == null) {
            finish()
            return
        }

        val btnBack: ImageButton = findViewById(R.id.btnBackSummary)
        btnBack.setOnClickListener { finish() }

        val btnReviewReps: Button = findViewById(R.id.btnSummaryReviewReps)
        val btnAskAssistant: Button = findViewById(R.id.btnSummaryAskAssistant)
        val btnShareCaregiver: Button = findViewById(R.id.btnSummaryShareCaregiver)
        val btnDone: Button = findViewById(R.id.btnSummaryDone)

        btnReviewReps.setOnClickListener {
            val intent = Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)
        }

        btnAskAssistant.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }

        btnShareCaregiver.setOnClickListener {
            currentSummary?.let { summary ->
                CaregiverShareHelper.shareSessionSummary(this, summary)
            }
        }

        btnDone.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        loadSession(sessionId)
    }

    private fun loadSession(sessionId: String) {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                db.sessionDao().getSessionById(sessionId)
            }
            val reps = withContext(Dispatchers.IO) {
                db.repDao().getRepsForSessionSync(sessionId)
            }
            val evidenceEvents = withContext(Dispatchers.IO) {
                db.evidenceEventDao().getEventsForSession(sessionId)
            }

            if (session == null) {
                finish()
                return@launch
            }

            val exerciseDef = ExerciseRegistry.getById(session.exerciseName)
            val repRecords = reps.map { rep ->
                RepRecord(
                    repNumber = rep.repNumber,
                    isValid = rep.isValid,
                    startTimestampMs = rep.startTimestampMs,
                    peakTimestampMs = rep.peakTimestampMs,
                    endTimestampMs = rep.endTimestampMs,
                    durationMs = rep.durationMs,
                    peakKneeAngle = rep.peakKneeAngle,
                    startKneeAngle = rep.startKneeAngle,
                    endKneeAngle = rep.endKneeAngle,
                    feedbackMessage = rep.feedbackMessage,
                    targetAngle = rep.targetAngle,
                    evidenceImagePath = rep.evidenceImagePath,
                    videoTimestampMs = rep.videoTimestampMs
                )
            }

            val summary = SessionSummary(
                sessionId = session.id,
                exerciseName = session.exerciseName,
                startedAt = session.startedAt,
                completedAt = session.completedAt,
                durationSeconds = session.durationSeconds,
                totalReps = session.totalReps,
                validReps = session.validReps,
                avgPeakKneeAngle = session.avgPeakKneeAngle,
                minKneeAngle = session.minKneeAngle,
                maxKneeAngle = session.maxKneeAngle,
                evidenceFailureCount = session.evidenceFailureCount,
                reps = repRecords,
                videoRecordingPath = session.videoRecordingPath,
                isRecordingEnabled = session.isRecordingEnabled
            )
            currentSummary = summary

            populateUi(summary, exerciseDef)
        }
    }

    private fun populateUi(summary: SessionSummary, exerciseDef: com.kinexmed.domain.model.ExerciseDefinition) {
        val tvExerciseName: TextView = findViewById(R.id.tvSummaryExerciseName)
        val tvDateTime: TextView = findViewById(R.id.tvSummaryDateTime)
        val tvFormScore: TextView = findViewById(R.id.tvSummaryFormScore)
        val tvValidReps: TextView = findViewById(R.id.tvSummaryValidReps)
        val tvAttemptedReps: TextView = findViewById(R.id.tvSummaryAttemptedReps)
        val tvDuration: TextView = findViewById(R.id.tvSummaryDuration)
        val tvAvgPeak: TextView = findViewById(R.id.tvSummaryAvgPeak)
        val tvTargetAngle: TextView = findViewById(R.id.tvSummaryTargetAngle)
        val tvPrimaryRejection: TextView = findViewById(R.id.tvSummaryPrimaryRejection)
        val tvEvidenceEvents: TextView = findViewById(R.id.tvSummaryEvidenceEvents)

        tvExerciseName.text = "${exerciseDef.displayName} Completed"

        val sdf = SimpleDateFormat("MMMM d, yyyy 'at' HH:mm", Locale.US)
        tvDateTime.text = sdf.format(Date(summary.startedAt))

        val score = if (summary.totalReps > 0) {
            (summary.validReps * 100) / summary.totalReps
        } else {
            0
        }
        tvFormScore.text = "$score%"

        tvValidReps.text = "${summary.validReps}"
        tvAttemptedReps.text = "of ${summary.totalReps} total"

        val minutes = summary.durationSeconds.toInt() / 60
        val seconds = summary.durationSeconds.toInt() % 60
        tvDuration.text = String.format("%02d:%02d", minutes, seconds)

        tvAvgPeak.text = "${summary.avgPeakKneeAngle.toInt()}°"
        tvTargetAngle.text = "Target: ${exerciseDef.targetMetricTarget}"

        val rejectedReps = summary.reps.filter { !it.isValid }
        if (rejectedReps.isNotEmpty()) {
            val mostCommon = rejectedReps.groupBy { it.feedbackMessage }
                .maxByOrNull { it.value.size }
            val count = mostCommon?.value?.size ?: 0
            val reason = mostCommon?.key?.ifBlank { "Range of motion limit" } ?: "Range limit"
            tvPrimaryRejection.text = "$reason ($count ${if (count == 1) "rep" else "reps"})"
        } else {
            tvPrimaryRejection.text = "None — All repetitions satisfied joint kinematics!"
            tvPrimaryRejection.setTextColor(android.graphics.Color.parseColor("#10B981"))
        }

        tvEvidenceEvents.text = "${summary.evidenceFailureCount} tracking interruptions recorded"
    }
}
