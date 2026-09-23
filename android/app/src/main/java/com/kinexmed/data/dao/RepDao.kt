package com.kinexmed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kinexmed.data.entity.RepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReps(reps: List<RepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRep(rep: RepEntity)

    @Query("SELECT * FROM session_reps WHERE sessionId = :sessionId ORDER BY repNumber ASC")
    fun getRepsForSession(sessionId: String): Flow<List<RepEntity>>

    @Query("SELECT * FROM session_reps WHERE sessionId = :sessionId ORDER BY repNumber ASC")
    suspend fun getRepsForSessionSync(sessionId: String): List<RepEntity>

    @Query("SELECT r.* FROM session_reps r INNER JOIN sessions s ON r.sessionId = s.id WHERE r.isValid = 0 AND s.startedAt >= :startTime AND s.startedAt <= :endTime ORDER BY r.startTimestampMs DESC")
    suspend fun getRejectedRepsBetween(startTime: Long, endTime: Long): List<RepEntity>

    @Query("SELECT r.* FROM session_reps r INNER JOIN sessions s ON r.sessionId = s.id WHERE r.repNumber = :repNumber AND s.startedAt >= :startTime AND s.startedAt <= :endTime ORDER BY s.startedAt DESC LIMIT 1")
    suspend fun getRepByNumberAndDate(repNumber: Int, startTime: Long, endTime: Long): RepEntity?

    @Query("SELECT r.* FROM session_reps r INNER JOIN sessions s ON r.sessionId = s.id WHERE r.repNumber = :repNumber ORDER BY s.startedAt DESC LIMIT 1")
    suspend fun getLatestRepByNumber(repNumber: Int): RepEntity?
}
