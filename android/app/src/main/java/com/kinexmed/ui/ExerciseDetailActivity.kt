package com.kinexmed.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kinexmed.R
import com.kinexmed.domain.model.ExerciseValidationStatus
import com.kinexmed.domain.registry.ExerciseRegistry

/**
 * Exercise Learning & Clinical Instructions Screen ("Learn" stage in patient journey).
 * Presents clinically accurate setup, starting posture, numbered movement cues,
 * completion standards, camera placement guidance, and common rejection pitfalls.
 */
class ExerciseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        val exerciseId = intent.getStringExtra(EXTRA_EXERCISE_ID) ?: "sit_to_stand"
        val exercise = ExerciseRegistry.getById(exerciseId)

        val btnBack: ImageButton = findViewById(R.id.btnBackDetail)
        val tvTitle: TextView = findViewById(R.id.tvDetailTitle)
        val tvStatusBadge: TextView = findViewById(R.id.tvDetailStatusBadge)
        val tvCategory: TextView = findViewById(R.id.tvDetailCategory)
        val tvDesc: TextView = findViewById(R.id.tvDetailDesc)

        val tvMetricLabel: TextView = findViewById(R.id.tvDetailMetricLabel)
        val tvMetricTarget: TextView = findViewById(R.id.tvDetailMetricTarget)
        val tvCameraDistance: TextView = findViewById(R.id.tvDetailCameraDistance)
        val tvCameraAngle: TextView = findViewById(R.id.tvDetailCameraAngle)

        val tvSetup: TextView = findViewById(R.id.tvDetailSetup)
        val tvPosture: TextView = findViewById(R.id.tvDetailPosture)
        val tvSteps: TextView = findViewById(R.id.tvDetailSteps)
        val tvCompletion: TextView = findViewById(R.id.tvDetailCompletion)
        val tvEvidenceReqs: TextView = findViewById(R.id.tvDetailEvidenceReqs)
        val tvRejections: TextView = findViewById(R.id.tvDetailRejections)

        val btnProceed: Button = findViewById(R.id.btnProceedToPrep)

        // Populate fields
        tvTitle.text = exercise.displayName
        tvCategory.text = "Category: ${exercise.category.displayName}"
        tvDesc.text = exercise.shortDescription

        when (exercise.status) {
            ExerciseValidationStatus.PHYSICALLY_DEMONSTRATED -> {
                tvStatusBadge.text = "DEMONSTRATED"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
                tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
            }
            ExerciseValidationStatus.IMPLEMENTED_MODULE -> {
                tvStatusBadge.text = "IMPLEMENTED MODULE"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_cyan)
                tvStatusBadge.setTextColor(Color.parseColor("#06B6D4"))
            }
            ExerciseValidationStatus.PLANNED -> {
                tvStatusBadge.text = "PLANNED"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
            }
        }

        tvMetricLabel.text = exercise.targetMetricName.uppercase()
        tvMetricTarget.text = exercise.targetMetricTarget
        tvCameraDistance.text = exercise.cameraDistance
        tvCameraAngle.text = exercise.cameraPlacement

        tvSetup.text = exercise.setupInstructions.ifEmpty { "Position your phone securely on a stable surface." }
        tvPosture.text = exercise.startingPosition.ifEmpty { "Assume comfortable upright starting posture." }

        val movementList = if (exercise.movementInstructions.isNotEmpty()) {
            exercise.movementInstructions.mapIndexed { idx, step -> "${idx + 1}. $step" }.joinToString("\n")
        } else {
            exercise.instructions.mapIndexed { idx, step -> "${idx + 1}. $step" }.joinToString("\n")
        }
        tvSteps.text = movementList.ifEmpty { "Perform the prescribed repetition with controlled tempo." }

        tvCompletion.text = exercise.completionInstructions.ifEmpty { "Complete each repetition under controlled motion." }

        val evidenceText = if (exercise.evidenceRequirements.isNotEmpty()) {
            exercise.evidenceRequirements.map { "• $it" }.joinToString("\n")
        } else {
            "• Keep full body or relevant joint inside the camera frame\n• Ensure steady lighting without strong backlight"
        }
        tvEvidenceReqs.text = evidenceText

        val rejectionsText = if (exercise.commonRejectionReasons.isNotEmpty()) {
            exercise.commonRejectionReasons.map { "• $it" }.joinToString("\n")
        } else {
            "• Incomplete joint range of motion\n• Moving too rapidly for steady frame observation"
        }
        tvRejections.text = rejectionsText

        btnProceed.text = "Proceed to ${exercise.displayName} Setup"

        btnBack.setOnClickListener {
            finish()
        }

        btnProceed.setOnClickListener {
            val prepIntent = Intent(this, ExercisePreparationActivity::class.java).apply {
                putExtra(ExercisePreparationActivity.EXTRA_EXERCISE_ID, exercise.type.id)
            }
            startActivity(prepIntent)
            finish()
        }
    }
}
