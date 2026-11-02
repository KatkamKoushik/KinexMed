package com.kinexmed.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.kinexmed.R

import android.widget.TextView
import com.kinexmed.domain.registry.ExerciseRegistry

/**
 * Pre-exercise preparation and setup screen.
 * Dynamically displays camera placement, distance, and setup guidance
 * for the selected rehabilitation exercise discovered from ExerciseRegistry.
 */
class ExercisePreparationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_preparation)

        val exerciseId = intent.getStringExtra(EXTRA_EXERCISE_ID) ?: "squat"
        val exercise = ExerciseRegistry.getById(exerciseId)

        val btnBack: ImageButton = findViewById(R.id.btnBackPrep)
        val btnStartTracking: Button = findViewById(R.id.btnStartTracking)

        val tvTitle: TextView = findViewById(R.id.tvPrepExerciseTitle)
        val tvSub: TextView = findViewById(R.id.tvPrepExerciseSub)
        val tvStep1: TextView = findViewById(R.id.tvPrepStep1Desc)
        val tvStep2: TextView = findViewById(R.id.tvPrepStep2Desc)
        val tvStep3: TextView = findViewById(R.id.tvPrepStep3Desc)

        tvTitle.text = exercise.displayName
        tvSub.text = exercise.shortDescription
        tvStep1.text = "Place phone at ${exercise.cameraPlacement}."
        tvStep2.text = "Step back ${exercise.cameraDistance}. Ensure ${exercise.requiredVisibility.lowercase()} is clearly in view."
        tvStep3.text = exercise.instructions.firstOrNull() ?: "Assume starting posture to begin exercise."

        btnStartTracking.text = "Start ${exercise.displayName} Tracking"

        btnBack.setOnClickListener {
            finish()
        }

        btnStartTracking.setOnClickListener {
            val intent = Intent(this, ExerciseActivity::class.java).apply {
                putExtra(ExerciseActivity.EXTRA_EXERCISE_ID, exercise.type.id)
            }
            startActivity(intent)
            finish()
        }
    }
}
