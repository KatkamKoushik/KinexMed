package com.kinexmed.data.repository

import com.kinexmed.data.dao.ChatMessageDao
import com.kinexmed.data.dao.EvidenceEventDao
import com.kinexmed.data.dao.ExerciseFrequencyStat
import com.kinexmed.data.dao.RepDao
import com.kinexmed.data.dao.SessionAggregateStats
import com.kinexmed.data.dao.SessionDao
import com.kinexmed.data.entity.ChatMessageEntity
import com.kinexmed.data.entity.EvidenceEventEntity
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SessionRepository(
    private val sessionDao: SessionDao,
    private val repDao: RepDao,
    private val evidenceEventDao: EvidenceEventDao? = null,
    private val chatMessageDao: ChatMessageDao? = null
) {
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    /**
     * Saves a completed session along with all individual reps and evidence episodes.
     * Executes on IO dispatcher so the camera/UI thread is never blocked.
     */
    suspend fun recordCompletedSession(
        summary: SessionSummary,
        deviceId: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
    ): SessionEntity = withContext(Dispatchers.IO) {
        val existingSession = sessionDao.getSessionById(summary.sessionId)
        if (existingSession != null) {
            return@withContext existingSession
        }

        val sessionEntity = SessionEntity(
            id = summary.sessionId,
            deviceId = deviceId,
            exerciseName = summary.exerciseName,
            startedAt = summary.startedAt,
            completedAt = summary.completedAt,
            durationSeconds = summary.durationSeconds,
            totalReps = summary.totalReps,
            validReps = summary.validReps,
            avgPeakKneeAngle = summary.avgPeakKneeAngle,
            minKneeAngle = summary.minKneeAngle,
            maxKneeAngle = summary.maxKneeAngle,
            evidenceFailureCount = summary.evidenceFailureCount,
            syncStatus = "PENDING",
            createdAt = System.currentTimeMillis(),
            videoRecordingPath = summary.videoRecordingPath,
            isRecordingEnabled = summary.isRecordingEnabled
        )

        sessionDao.insertSession(sessionEntity)

        val repEntities = summary.reps.map { rep ->
            val reasonsJson = JSONArray(rep.failureReasons).toString()
            RepEntity(
                sessionId = summary.sessionId,
                repNumber = rep.repNumber,
                isValid = rep.isValid,
                startTimestampMs = rep.startTimestampMs,
                peakTimestampMs = rep.peakTimestampMs,
                endTimestampMs = rep.endTimestampMs,
                durationMs = rep.durationMs,
                peakKneeAngle = rep.peakKneeAngle,
                startKneeAngle = rep.startKneeAngle,
                endKneeAngle = rep.endKneeAngle,
                feedbackMessage = rep.feedbackMessage,
                failureReasonsJson = reasonsJson,
                evidenceImagePath = rep.evidenceImagePath,
                videoTimestampMs = rep.videoTimestampMs,
                targetAngle = rep.targetAngle,
                exerciseName = summary.exerciseName
            )
        }

        if (repEntities.isNotEmpty()) {
            repDao.insertReps(repEntities)
        }

        if (summary.evidenceEpisodes.isNotEmpty() && evidenceEventDao != null) {
            val eventEntities = summary.evidenceEpisodes.map { ep ->
                EvidenceEventEntity(
                    sessionId = summary.sessionId,
                    eventType = ep.reason.name,
                    startTimeMs = ep.startTimeMs,
                    endTimeMs = ep.endTimeMs,
                    durationMs = ep.durationMs,
                    frameCount = ep.frameCount,
                    details = "Aggregated episode of ${ep.frameCount} frames"
                )
            }
            evidenceEventDao.insertEvents(eventEntities)
        }

        sessionEntity
    }

    suspend fun getEventsForSessionSync(sessionId: String): List<EvidenceEventEntity> = withContext(Dispatchers.IO) {
        evidenceEventDao?.getEventsForSessionSync(sessionId) ?: emptyList()
    }

    fun getRepsForSession(sessionId: String): Flow<List<RepEntity>> {
        return repDao.getRepsForSession(sessionId)
    }

    suspend fun getRepsForSessionSync(sessionId: String): List<RepEntity> = withContext(Dispatchers.IO) {
        repDao.getRepsForSessionSync(sessionId)
    }

    suspend fun getPendingSessions(): List<SessionEntity> = withContext(Dispatchers.IO) {
        sessionDao.getPendingSessions()
    }

    suspend fun getAllSessionsSync(): List<SessionEntity> = withContext(Dispatchers.IO) {
        sessionDao.getAllSessionsList()
    }

    suspend fun getSessionById(sessionId: String): SessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(sessionId)
    }

    suspend fun markSessionSynced(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.markSessionSynced(sessionId)
    }

    // Memory Retrieval & Aggregation queries
    suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<SessionEntity> = withContext(Dispatchers.IO) {
        sessionDao.getSessionsBetween(startTime, endTime)
    }

    suspend fun getMostRecentSession(): SessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getMostRecentSession()
    }

    suspend fun getAggregatedStats(startTime: Long, endTime: Long): SessionAggregateStats = withContext(Dispatchers.IO) {
        sessionDao.getAggregatedStats(startTime, endTime)
    }

    suspend fun getExerciseFrequencies(startTime: Long, endTime: Long): List<ExerciseFrequencyStat> = withContext(Dispatchers.IO) {
        sessionDao.getExerciseFrequencies(startTime, endTime)
    }

    suspend fun getAllTimeExerciseFrequencies(): List<ExerciseFrequencyStat> = withContext(Dispatchers.IO) {
        sessionDao.getAllTimeExerciseFrequencies()
    }

    suspend fun getRejectedRepsBetween(startTime: Long, endTime: Long): List<RepEntity> = withContext(Dispatchers.IO) {
        repDao.getRejectedRepsBetween(startTime, endTime)
    }

    suspend fun getRepByNumberAndDate(repNumber: Int, startTime: Long, endTime: Long): RepEntity? = withContext(Dispatchers.IO) {
        repDao.getRepByNumberAndDate(repNumber, startTime, endTime)
    }

    suspend fun getLatestRepByNumber(repNumber: Int): RepEntity? = withContext(Dispatchers.IO) {
        repDao.getLatestRepByNumber(repNumber)
    }

    // Chat Message Persistence
    suspend fun insertChatMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        chatMessageDao?.insertMessage(message)
    }

    fun getAllChatMessages(): Flow<List<ChatMessageEntity>> {
        return chatMessageDao?.getAllMessages() ?: emptyFlow()
    }

    suspend fun getAllChatMessagesSync(): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        chatMessageDao?.getAllMessagesSync() ?: emptyList()
    }

    suspend fun clearChatMessages() = withContext(Dispatchers.IO) {
        chatMessageDao?.clearAllMessages()
    }
}
