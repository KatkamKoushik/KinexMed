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

    private lateinit var chipAll: Button
    private lateinit var chipLowerBody: Button
    private lateinit var chipUpperBody: Button
    private lateinit var chipFunctional: Button
    private lateinit var chipBalance: Button

    private var selectedCategory: com.kinexmed.domain.model.ExerciseCategory? = null
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

        chipAll = findViewById(R.id.chipAll)
        chipLowerBody = findViewById(R.id.chipLowerBody)
        chipUpperBody = findViewById(R.id.chipUpperBody)
        chipFunctional = findViewById(R.id.chipFunctional)
        chipBalance = findViewById(R.id.chipBalance)

        chipAll.setOnClickListener { selectCategory(null) }
        chipLowerBody.setOnClickListener { selectCategory(com.kinexmed.domain.model.ExerciseCategory.LOWER_BODY) }
        chipUpperBody.setOnClickListener { selectCategory(com.kinexmed.domain.model.ExerciseCategory.UPPER_BODY) }
        chipFunctional.setOnClickListener { selectCategory(com.kinexmed.domain.model.ExerciseCategory.FUNCTIONAL_MOBILITY) }
        chipBalance.setOnClickListener { selectCategory(com.kinexmed.domain.model.ExerciseCategory.BALANCE) }

        btnHistory.setOnClickListener {
            val intent = Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun selectCategory(category: com.kinexmed.domain.model.ExerciseCategory?) {
        selectedCategory = category
        updateChipStyles()
        populateExercises()
    }

    private fun updateChipStyles() {
        val activeBg = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#06B6D4"))
        val inactiveBg = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#161B26"))
        val activeText = android.graphics.Color.parseColor("#FFFFFF")
        val inactiveText = android.graphics.Color.parseColor("#94A3B8")

        val chips = listOf(
            chipAll to (selectedCategory == null),
            chipLowerBody to (selectedCategory == com.kinexmed.domain.model.ExerciseCategory.LOWER_BODY),
            chipUpperBody to (selectedCategory == com.kinexmed.domain.model.ExerciseCategory.UPPER_BODY),
            chipFunctional to (selectedCategory == com.kinexmed.domain.model.ExerciseCategory.FUNCTIONAL_MOBILITY),
            chipBalance to (selectedCategory == com.kinexmed.domain.model.ExerciseCategory.BALANCE)
        )

        for ((chip, isActive) in chips) {
            chip.backgroundTintList = if (isActive) activeBg else inactiveBg
            chip.setTextColor(if (isActive) activeText else inactiveText)
        }
    }

    private fun populateExercises() {
        llExercisesContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val list = if (selectedCategory == null) {
            ExerciseRegistry.exercises
        } else {
            ExerciseRegistry.getByCategory(selectedCategory!!)
        }

        for (exercise in list) {
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

            when (exercise.status) {
                ExerciseValidationStatus.PHYSICALLY_DEMONSTRATED -> {
                    tvBadge.text = "DEMONSTRATED"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_green)
                    tvBadge.setTextColor(android.graphics.Color.parseColor("#10B981"))
                    btnStart.isEnabled = true
                    btnStart.text = "Start ${exercise.displayName}"
                    btnStart.setOnClickListener {
                        val intent = Intent(this, ExercisePreparationActivity::class.java).apply {
                            putExtra(ExercisePreparationActivity.EXTRA_EXERCISE_ID, exercise.type.id)
                        }
                        startActivity(intent)
                    }
                }
                ExerciseValidationStatus.IMPLEMENTED_MODULE -> {
                    tvBadge.text = "IMPLEMENTED MODULE"
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
                }
                ExerciseValidationStatus.PLANNED -> {
                    tvBadge.text = "PLANNED"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    tvBadge.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                    btnStart.isEnabled = false
                    btnStart.text = "Under Development"
                }
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
