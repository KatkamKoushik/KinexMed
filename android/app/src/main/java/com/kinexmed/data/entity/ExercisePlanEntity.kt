package com.kinexmed.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a user- or clinician-configured physical rehabilitation exercise plan.
 * Deterministic schedule indicating target frequency and prescribed exercises.
 */
@Entity(tableName = "exercise_plans")
data class ExercisePlanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isActive: Boolean = false,
    val frequencyPerWeek: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
