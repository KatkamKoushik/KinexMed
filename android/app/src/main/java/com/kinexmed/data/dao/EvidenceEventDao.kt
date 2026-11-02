package com.kinexmed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kinexmed.data.entity.EvidenceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EvidenceEventEntity>)

    @Query("SELECT * FROM evidence_events WHERE sessionId = :sessionId ORDER BY startTimeMs ASC")
    fun getEventsForSession(sessionId: String): Flow<List<EvidenceEventEntity>>

    @Query("SELECT * FROM evidence_events WHERE sessionId = :sessionId ORDER BY startTimeMs ASC")
    suspend fun getEventsForSessionSync(sessionId: String): List<EvidenceEventEntity>
}
