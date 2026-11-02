package com.kinexmed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kinexmed.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

data class SessionAggregateStats(
    val totalSessions: Int = 0,
    val totalValidReps: Int = 0,
    val totalReps: Int = 0,
    val avgRom: Double = 0.0,
    val totalDurationSeconds: Double = 0.0
)

data class ExerciseFrequencyStat(
    val exerciseName: String,
    val sessionCount: Int,
    val totalValidReps: Int
)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    suspend fun getAllSessionsList(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET syncStatus = 'SYNCED' WHERE id = :sessionId")
    suspend fun markSessionSynced(sessionId: String)

    @Query("SELECT * FROM sessions WHERE startedAt >= :startTime AND startedAt <= :endTime ORDER BY startedAt DESC")
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun getMostRecentSession(): SessionEntity?

    @Query("SELECT COUNT(*) as totalSessions, COALESCE(SUM(validReps), 0) as totalValidReps, COALESCE(SUM(totalReps), 0) as totalReps, COALESCE(AVG(avgPeakKneeAngle), 0.0) as avgRom, COALESCE(SUM(durationSeconds), 0.0) as totalDurationSeconds FROM sessions WHERE startedAt >= :startTime AND startedAt <= :endTime")
    suspend fun getAggregatedStats(startTime: Long, endTime: Long): SessionAggregateStats

    @Query("SELECT exerciseName, COUNT(*) as sessionCount, COALESCE(SUM(validReps), 0) as totalValidReps FROM sessions WHERE startedAt >= :startTime AND startedAt <= :endTime GROUP BY exerciseName ORDER BY totalValidReps DESC")
    suspend fun getExerciseFrequencies(startTime: Long, endTime: Long): List<ExerciseFrequencyStat>

    @Query("SELECT exerciseName, COUNT(*) as sessionCount, COALESCE(SUM(validReps), 0) as totalValidReps FROM sessions GROUP BY exerciseName ORDER BY totalValidReps DESC")
    suspend fun getAllTimeExerciseFrequencies(): List<ExerciseFrequencyStat>
}
