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
import com.kinexmed.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.kinexmed.domain.model.ExerciseValidationStatus
import com.kinexmed.domain.registry.ExerciseRegistry

/**
 * Main patient home screen for KinexMed.
 * Dynamically discovers exercises from ExerciseRegistry.
 * Provides exercise prescription selection, clinical rehabilitation objectives,
 * local Room persistence KPIs, and navigation to Session History & Settings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvTotalSessionsKpi: TextView
    private lateinit var tvTotalValidRepsKpi: TextView
    private lateinit var tvSyncStatusSummary: TextView
    private lateinit var llExercisesContainer: LinearLayout
    private lateinit var btnHistory: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        populateExercises()
        initData()
    }

    private fun initViews() {
        tvTotalSessionsKpi = findViewById(R.id.tvTotalSessionsKpi)
        tvTotalValidRepsKpi = findViewById(R.id.tvTotalValidRepsKpi)
        tvSyncStatusSummary = findViewById(R.id.tvSyncStatusSummary)
        llExercisesContainer = findViewById(R.id.llExercisesContainer)
        btnHistory = findViewById(R.id.btnHistory)
        btnSettings = findViewById(R.id.btnSettings)

        btnHistory.setOnClickListener {
            val intent = Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun populateExercises() {
        llExercisesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (exercise in ExerciseRegistry.exercises) {
            val cardView = inflater.inflate(R.layout.item_exercise_card, llExercisesContainer, false)

            val tvTitle: TextView = cardView.findViewById(R.id.tvExerciseTitle)
            val tvBadge: TextView = cardView.findViewById(R.id.tvExerciseBadge)
            val tvDesc: TextView = cardView.findViewById(R.id.tvExerciseDesc)
            val tvMetricLabel: TextView = cardView.findViewById(R.id.tvMetricLabel)
            val tvMetricTarget: TextView = cardView.findViewById(R.id.tvMetricTarget)
            val tvDistance: TextView = cardView.findViewById(R.id.tvCameraDistance)
            val btnStart: Button = cardView.findViewById(R.id.btnStartExercise)

            tvTitle.text = exercise.displayName
            tvDesc.text = exercise.shortDescription
            tvMetricLabel.text = exercise.targetMetricName.uppercase()
            tvMetricTarget.text = exercise.targetMetricTarget
            tvDistance.text = exercise.cameraDistance

            if (exercise.status == ExerciseValidationStatus.VALIDATED) {
                tvBadge.text = "VALIDATED"
                tvBadge.setBackgroundResource(R.drawable.bg_badge_cyan)
                tvBadge.setTextColor(android.graphics.Color.parseColor("#06B6D4"))
                btnStart.isEnabled = true
                btnStart.text = "Start ${exercise.displayName}"
                btnStart.setOnClickListener {
                    val intent = Intent(this, ExercisePreparationActivity::class.java).apply {
                        putExtra(ExercisePreparationActivity.EXTRA_EXERCISE_ID, exercise.type.id)
                    }
                    startActivity(intent)
                }
            } else {
                tvBadge.text = "NOT YET VALIDATED"
                tvBadge.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                btnStart.isEnabled = false
                btnStart.text = "Under Validation"
            }

            llExercisesContainer.addView(cardView)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::sessionRepository.isInitialized) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val syncMgr = com.kinexmed.sync.SyncManager(sessionRepository, this@MainActivity)
                    syncMgr.syncPendingSessions()
                } catch (_: Exception) {}
            }
        }
    }

    private fun initData() {
        val db = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(db.sessionDao(), db.repDao(), db.evidenceEventDao())

        // Observe real Room database sessions reactively
        lifecycleScope.launch {
            sessionRepository.allSessions.collectLatest { sessions ->
                val totalSessions = sessions.size
                val totalValidReps = sessions.sumOf { it.validReps }
                val pendingSync = sessions.count { it.syncStatus == "PENDING" }

                tvTotalSessionsKpi.text = "$totalSessions"
                tvTotalValidRepsKpi.text = "$totalValidReps"

                tvSyncStatusSummary.text = if (pendingSync > 0) {
                    "$pendingSync pending upload"
                } else if (totalSessions > 0) {
                    "All sessions synchronized"
                } else {
                    "Ready for first session"
                }
            }
        }
    }
}
