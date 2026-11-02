package com.kinexmed.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kinexmed.data.entity.SessionFeedbackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for clinician feedback linked to exercise sessions.
 */
@Dao
interface SessionFeedbackDao {

    @Query("SELECT * FROM session_feedback WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getFeedbackForSessionFlow(sessionId: String): Flow<List<SessionFeedbackEntity>>

    @Query("SELECT * FROM session_feedback WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getFeedbackForSession(sessionId: String): List<SessionFeedbackEntity>

    @Query("SELECT * FROM session_feedback ORDER BY createdAt DESC")
    suspend fun getAllFeedback(): List<SessionFeedbackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: SessionFeedbackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFeedback(feedbackList: List<SessionFeedbackEntity>)
}
