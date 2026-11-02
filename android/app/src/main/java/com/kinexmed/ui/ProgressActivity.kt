package com.kinexmed.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.consistency.ConsistencyCalculator
import com.kinexmed.domain.registry.ExerciseRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Patient Weekly & Monthly Progress Screen.
 * Visualizes adherence metrics, consistency streak, exercise volume distribution,
 * and rejection reason analysis derived strictly from local SQLite sessions.
 */
class ProgressActivity : AppCompatActivity() {

    private enum class ViewMode { WEEK, MONTH }
    private var currentMode = ViewMode.WEEK

    private lateinit var btnToggleWeek: Button
    private lateinit var btnToggleMonth: Button
    private lateinit var tvCurrentStreak: TextView
    private lateinit var tvLongestStreak: TextView
    private lateinit var tvWeeklyGoal: TextView
    private lateinit var tvValidReps: TextView
    private lateinit var tvAttemptedReps: TextView
    private lateinit var tvAdherence: TextView
    private lateinit var tvTotalDuration: TextView

    private lateinit var llExerciseVolumeContainer: LinearLayout
    private lateinit var tvEmptyVolume: TextView
    private lateinit var llRejectionsContainer: LinearLayout
    private lateinit var tvEmptyRejections: TextView

    private var allSessions: List<SessionEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        initViews()
        loadData()
    }

    private fun initViews() {
        val btnBack: ImageButton = findViewById(R.id.btnBackProgress)
        btnBack.setOnClickListener { finish() }

        btnToggleWeek = findViewById(R.id.btnToggleWeek)
        btnToggleMonth = findViewById(R.id.btnToggleMonth)
        tvCurrentStreak = findViewById(R.id.tvProgressCurrentStreak)
        tvLongestStreak = findViewById(R.id.tvProgressLongestStreak)
        tvWeeklyGoal = findViewById(R.id.tvProgressWeeklyGoal)
        tvValidReps = findViewById(R.id.tvProgressValidReps)
        tvAttemptedReps = findViewById(R.id.tvProgressAttemptedReps)
        tvAdherence = findViewById(R.id.tvProgressAdherence)
        tvTotalDuration = findViewById(R.id.tvProgressTotalDuration)

        llExerciseVolumeContainer = findViewById(R.id.llProgressExerciseVolumeContainer)
        tvEmptyVolume = findViewById(R.id.tvProgressEmptyVolume)
        llRejectionsContainer = findViewById(R.id.llProgressRejectionsContainer)
        tvEmptyRejections = findViewById(R.id.tvProgressEmptyRejections)

        btnToggleWeek.setOnClickListener {
            if (currentMode != ViewMode.WEEK) {
                currentMode = ViewMode.WEEK
                updateToggleStyles()
                renderProgress()
            }
        }

        btnToggleMonth.setOnClickListener {
            if (currentMode != ViewMode.MONTH) {
                currentMode = ViewMode.MONTH
                updateToggleStyles()
                renderProgress()
            }
        }
    }

    private fun updateToggleStyles() {
        if (currentMode == ViewMode.WEEK) {
            btnToggleWeek.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#06B6D4"))
            btnToggleWeek.setTextColor(Color.WHITE)
            btnToggleMonth.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            btnToggleMonth.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            btnToggleMonth.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#06B6D4"))
            btnToggleMonth.setTextColor(Color.WHITE)
            btnToggleWeek.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            btnToggleWeek.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun loadData() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            allSessions = withContext(Dispatchers.IO) {
                db.sessionDao().getAllSessionsList()
            }
            renderStreak()
            renderProgress()
        }
    }

    private fun renderStreak() {
        val currentStreak = ConsistencyCalculator.calculateCurrentStreak(allSessions)
        val longestStreak = ConsistencyCalculator.calculateLongestStreak(allSessions)
        val weeklyStat = ConsistencyCalculator.calculateWeeklyProgress(allSessions, targetSessions = 5)

        tvCurrentStreak.text = "$currentStreak ${if (currentStreak == 1) "Day" else "Days"}"
        tvLongestStreak.text = "Best streak: $longestStreak ${if (longestStreak == 1) "day" else "days"}"
        tvWeeklyGoal.text = "${weeklyStat.completedSessions} / ${weeklyStat.targetSessions}"
    }

    private fun renderProgress() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            if (currentMode == ViewMode.WEEK) {
                val stat = ConsistencyCalculator.calculateWeeklyProgress(allSessions)
                tvValidReps.text = "${stat.validReps}"
                tvAttemptedReps.text = "Attempted: ${stat.attemptedReps}"
                tvAdherence.text = if (stat.attemptedReps > 0) String.format("%.0f%%", stat.adherencePercent) else "--%"

                val minutes = stat.totalDurationSeconds.toInt() / 60
                val seconds = stat.totalDurationSeconds.toInt() % 60
                tvTotalDuration.text = String.format("Active: %02d:%02d", minutes, seconds)

                renderVolumeDistribution(stat.exerciseDistribution)
                renderRejectionsForPeriod(db, isWeek = true)
            } else {
                val stat = ConsistencyCalculator.calculateMonthlyProgress(allSessions)
                tvValidReps.text = "${stat.validReps}"
                tvAttemptedReps.text = "Attempted: ${stat.attemptedReps}"
                tvAdherence.text = if (stat.attemptedReps > 0) String.format("%.0f%%", stat.adherencePercent) else "--%"

                val hours = stat.totalDurationSeconds.toInt() / 3600
                val minutes = (stat.totalDurationSeconds.toInt() % 3600) / 60
                tvTotalDuration.text = if (hours > 0) String.format("Active: %dh %02dm", hours, minutes) else String.format("Active: %02dm", minutes)

                renderVolumeDistribution(stat.exerciseDistribution)
                renderRejectionsForPeriod(db, isWeek = false)
            }
        }
    }

    private fun renderVolumeDistribution(distribution: Map<String, Int>) {
        llExerciseVolumeContainer.removeAllViews()
        if (distribution.isEmpty()) {
            llExerciseVolumeContainer.addView(tvEmptyVolume)
            tvEmptyVolume.visibility = View.VISIBLE
            return
        }

        tvEmptyVolume.visibility = View.GONE
        for ((exerciseId, count) in distribution) {
            val exerciseDef = ExerciseRegistry.getById(exerciseId)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 6, 0, 6)
                }
            }

            val tvName = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = exerciseDef.displayName
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
            }

            val tvReps = TextView(this).apply {
                text = "$count valid reps"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 13f
                paint.isFakeBoldText = true
            }

            row.addView(tvName)
            row.addView(tvReps)
            llExerciseVolumeContainer.addView(row)
        }
    }

    private suspend fun renderRejectionsForPeriod(db: AppDatabase, isWeek: Boolean) {
        val nowMs = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            if (isWeek) {
                firstDayOfWeek = java.util.Calendar.MONDAY
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            } else {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis

        val rejectedReps = withContext(Dispatchers.IO) {
            db.repDao().getRejectedRepsBetween(startTime, nowMs)
        }

        llRejectionsContainer.removeAllViews()
        if (rejectedReps.isEmpty()) {
            llRejectionsContainer.addView(tvEmptyRejections)
            tvEmptyRejections.visibility = View.VISIBLE
            return
        }

        tvEmptyRejections.visibility = View.GONE

        // Group by feedback message / failure reason
        val reasonCounts = mutableMapOf<String, Int>()
        for (rep in rejectedReps) {
            val msg = if (rep.feedbackMessage.isNotBlank()) rep.feedbackMessage else "Kinematic threshold not reached"
            reasonCounts[msg] = (reasonCounts[msg] ?: 0) + 1
        }

        for ((reason, count) in reasonCounts.entries.sortedByDescending { it.value }) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 6, 0, 6)
                }
            }

            val tvReason = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = reason
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 13f
            }

            val tvCount = TextView(this).apply {
                text = "$count ${if (count == 1) "rep" else "reps"}"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 13f
                paint.isFakeBoldText = true
            }

            row.addView(tvReason)
            row.addView(tvCount)
            llRejectionsContainer.addView(row)
        }
    }
}
