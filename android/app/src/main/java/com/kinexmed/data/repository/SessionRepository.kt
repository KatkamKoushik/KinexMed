package com.kinexmed.data.repository

import com.kinexmed.data.dao.EvidenceEventDao
import com.kinexmed.data.dao.RepDao
import com.kinexmed.data.dao.SessionDao
import com.kinexmed.data.entity.EvidenceEventEntity
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.domain.model.RepRecord
import com.kinexmed.domain.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class SessionRepository(
    private val sessionDao: SessionDao,
    private val repDao: RepDao,
    private val evidenceEventDao: EvidenceEventDao? = null
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
            createdAt = System.currentTimeMillis()
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
                failureReasonsJson = reasonsJson
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

    suspend fun markSessionSynced(sessionId: String) = withContext(Dispatchers.IO) {
        sessionDao.markSessionSynced(sessionId)
    }
}
