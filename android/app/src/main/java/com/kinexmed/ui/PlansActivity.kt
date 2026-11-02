package com.kinexmed.ui

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.entity.ExercisePlanEntity
import com.kinexmed.data.entity.PlanExerciseEntity
import com.kinexmed.data.repository.PlanRepository
import com.kinexmed.domain.registry.ExerciseRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Patient Exercise Plans Screen.
 * Allows viewing, activating, and creating user- or clinician-configured rehabilitation plans.
 * Explicitly non-prescriptive: deterministic schedules configuring targets and exercises.
 */
class PlansActivity : AppCompatActivity() {

    private lateinit var planRepository: PlanRepository

    private lateinit var tvActivePlanName: TextView
    private lateinit var tvActivePlanBadge: TextView
    private lateinit var tvActivePlanDesc: TextView
    private lateinit var tvActivePlanFrequency: TextView
    private lateinit var llActivePlanExercises: LinearLayout
    private lateinit var tvEmptyPlanExercises: TextView

    private lateinit var llAllPlansContainer: LinearLayout
    private lateinit var tvEmptyAllPlans: TextView
    private lateinit var btnCreatePlan: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plans)

        val db = AppDatabase.getInstance(this)
        planRepository = PlanRepository(db.exercisePlanDao())

        initViews()
        observeData()
    }

    private fun initViews() {
        val btnBack: ImageButton = findViewById(R.id.btnBackPlans)
        btnBack.setOnClickListener { finish() }

        tvActivePlanName = findViewById(R.id.tvActivePlanName)
        tvActivePlanBadge = findViewById(R.id.tvActivePlanBadge)
        tvActivePlanDesc = findViewById(R.id.tvActivePlanDesc)
        tvActivePlanFrequency = findViewById(R.id.tvActivePlanFrequency)
        llActivePlanExercises = findViewById(R.id.llActivePlanExercises)
        tvEmptyPlanExercises = findViewById(R.id.tvEmptyPlanExercises)

        llAllPlansContainer = findViewById(R.id.llAllPlansContainer)
        tvEmptyAllPlans = findViewById(R.id.tvEmptyAllPlans)
        btnCreatePlan = findViewById(R.id.btnCreatePlan)

        btnCreatePlan.setOnClickListener {
            showCreatePlanDialog()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            planRepository.activePlan.collectLatest { activePlan ->
                if (activePlan != null) {
                    tvActivePlanName.text = activePlan.name
                    tvActivePlanBadge.visibility = View.VISIBLE
                    tvActivePlanDesc.text = activePlan.description.ifEmpty { "User/clinician-configured plan." }
                    tvActivePlanFrequency.text = "Target Frequency: ${activePlan.frequencyPerWeek} sessions / week"

                    val exercises = withContext(Dispatchers.IO) {
                        planRepository.getPlanExercises(activePlan.id)
                    }
                    renderActivePlanExercises(exercises)
                } else {
                    tvActivePlanName.text = "No active exercise plan"
                    tvActivePlanBadge.visibility = View.GONE
                    tvActivePlanDesc.text = "Select a plan below or create a new one to track your prescribed routine."
                    tvActivePlanFrequency.text = "Frequency: Not set"
                    llActivePlanExercises.removeAllViews()
                    llActivePlanExercises.addView(tvEmptyPlanExercises)
                    tvEmptyPlanExercises.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            planRepository.allPlans.collectLatest { plans ->
                renderAllPlans(plans)
            }
        }
    }

    private fun renderActivePlanExercises(exercises: List<PlanExerciseEntity>) {
        llActivePlanExercises.removeAllViews()
        if (exercises.isEmpty()) {
            llActivePlanExercises.addView(tvEmptyPlanExercises)
            tvEmptyPlanExercises.visibility = View.VISIBLE
            return
        }

        tvEmptyPlanExercises.visibility = View.GONE
        for (item in exercises) {
            val def = ExerciseRegistry.getById(item.exerciseType)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "• ${def.displayName}"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
            }

            val tvTargets = TextView(this).apply {
                text = "${item.targetSets} sets × ${item.targetReps} reps"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                paint.isFakeBoldText = true
            }

            row.addView(tvTitle)
            row.addView(tvTargets)
            llActivePlanExercises.addView(row)
        }
    }

    private fun renderAllPlans(plans: List<ExercisePlanEntity>) {
        llAllPlansContainer.removeAllViews()
        if (plans.isEmpty()) {
            llAllPlansContainer.addView(tvEmptyAllPlans)
            tvEmptyAllPlans.visibility = View.VISIBLE
            return
        }

        tvEmptyAllPlans.visibility = View.GONE
        for (plan in plans) {
            val card = androidx.cardview.widget.CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 6, 0, 6)
                }
                radius = 12f
                setCardBackgroundColor(Color.parseColor("#111622"))
            }

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = plan.name
                setTextColor(Color.WHITE)
                textSize = 15f
                paint.isFakeBoldText = true
            }

            headerRow.addView(tvTitle)

            if (plan.isActive) {
                val tvBadge = TextView(this).apply {
                    text = "ACTIVE"
                    textSize = 10f
                    paint.isFakeBoldText = true
                    setPadding(12, 4, 12, 4)
                    setBackgroundResource(R.drawable.bg_badge_green)
                    setTextColor(Color.parseColor("#10B981"))
                }
                headerRow.addView(tvBadge)
            } else {
                val btnActivate = Button(this).apply {
                    text = "Activate"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#06B6D4"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        36.dpToPx()
                    )
                    setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            planRepository.setActivePlan(plan.id)
                        }
                    }
                }
                headerRow.addView(btnActivate)
            }

            val tvDesc = TextView(this).apply {
                text = plan.description.ifEmpty { "Target: ${plan.frequencyPerWeek} sessions / week" }
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }

            inner.addView(headerRow)
            inner.addView(tvDesc)
            card.addView(inner)
            llAllPlansContainer.addView(card)
        }
    }

    private fun showCreatePlanDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 16.dpToPx())
        }

        val etPlanName = EditText(this).apply {
            hint = "Plan Name (e.g. Post-Op Knee Routine)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
        }

        val etPlanDesc = EditText(this).apply {
            hint = "Description or Clinical Notes"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
        }

        val etFrequency = EditText(this).apply {
            hint = "Sessions per week (e.g. 5)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("5")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
        }

        dialogView.addView(etPlanName)
        dialogView.addView(etPlanDesc)
        dialogView.addView(etFrequency)

        AlertDialog.Builder(this)
            .setTitle("Create Exercise Plan")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etPlanName.text.toString().trim()
                val desc = etPlanDesc.text.toString().trim()
                val freq = etFrequency.text.toString().trim().toIntOrNull() ?: 5

                if (name.isNotEmpty()) {
                    val newPlanId = UUID.randomUUID().toString()
                    val plan = ExercisePlanEntity(
                        id = newPlanId,
                        name = name,
                        description = desc,
                        isActive = true,
                        frequencyPerWeek = freq
                    )
                    // Default prescribed exercises: Sit-to-Stand and Bilateral Squat
                    val exercises = listOf(
                        PlanExerciseEntity(
                            id = UUID.randomUUID().toString(),
                            planId = newPlanId,
                            exerciseType = "sit_to_stand",
                            targetSets = 3,
                            targetReps = 10,
                            orderIndex = 0
                        ),
                        PlanExerciseEntity(
                            id = UUID.randomUUID().toString(),
                            planId = newPlanId,
                            exerciseType = "squat",
                            targetSets = 3,
                            targetReps = 10,
                            orderIndex = 1
                        )
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        planRepository.savePlanWithExercises(plan, exercises)
                    }
                    Toast.makeText(this, "Plan created and activated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
