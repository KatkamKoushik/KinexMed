package com.kinexmed.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Exercise prescribed within an ExercisePlan.
 * Specifies target sets, target reps, and exercise type.
 */
@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = ExercisePlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"])]
)
data class PlanExerciseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val exerciseType: String,
    val targetSets: Int = 3,
    val targetReps: Int = 10,
    val orderIndex: Int = 0
)
