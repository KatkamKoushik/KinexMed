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
}
