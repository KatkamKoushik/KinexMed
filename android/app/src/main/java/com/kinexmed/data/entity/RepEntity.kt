package com.kinexmed.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "session_reps",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class RepEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val repNumber: Int,
    val isValid: Boolean,
    val startTimestampMs: Long,
    val peakTimestampMs: Long,
    val endTimestampMs: Long,
    val durationMs: Long,
    val peakKneeAngle: Double,
    val startKneeAngle: Double,
    val endKneeAngle: Double,
    val feedbackMessage: String,
    val failureReasonsJson: String = "[]",
    val evidenceImagePath: String? = null,
    val videoTimestampMs: Long? = null,
    val targetAngle: Double = 0.0,
    val exerciseName: String = ""
)
