package com.kinexmed.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deviceId: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim(),
    val exerciseName: String = "squat",
    val startedAt: Long,
    val completedAt: Long,
    val durationSeconds: Double,
    val totalReps: Int,
    val validReps: Int,
    val avgPeakKneeAngle: Double,
    val minKneeAngle: Double,
    val maxKneeAngle: Double,
    val evidenceFailureCount: Int,
    val syncStatus: String = "PENDING", // PENDING, SYNCED
    val createdAt: Long = System.currentTimeMillis(),
    val videoRecordingPath: String? = null,
    val isRecordingEnabled: Boolean = false
)
