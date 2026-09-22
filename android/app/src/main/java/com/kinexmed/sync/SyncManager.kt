package com.kinexmed.sync

import android.content.Context
import android.util.Log
import com.kinexmed.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Synchronizes completed local Room sessions with the FastAPI backend.
 * Tries user-configured URL from SharedPreferences, local loopback (via ADB reverse),
 * direct LAN IP, and emulator endpoints.
 */
class SyncManager(
    private val sessionRepository: SessionRepository,
    private val context: Context? = null,
    private val customCandidateUrls: List<String>? = null
) {
    companion object {
        private const val TAG = "KinexMedSync"
        const val PREFS_NAME = "kinexmed_settings"
        const val KEY_SERVER_URL = "server_url"
        const val DEFAULT_SERVER_URL = "http://127.0.0.1:8000"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getCandidateUrls(): List<String> {
        if (customCandidateUrls != null) return customCandidateUrls

        val configuredUrl = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_SERVER_URL, null)?.trim()?.removeSuffix("/")

        val list = mutableListOf<String>()
        if (!configuredUrl.isNullOrEmpty()) {
            list.add(configuredUrl)
        }
        if (!list.contains("http://127.0.0.1:8000")) list.add("http://127.0.0.1:8000") // ADB reverse port forward
        if (!list.contains("http://192.168.0.11:8000")) list.add("http://192.168.0.11:8000") // Wi-Fi LAN IP (PC)
        if (!list.contains("http://192.168.0.10:8000")) list.add("http://192.168.0.10:8000") // Wi-Fi LAN fallback
        if (!list.contains("http://10.0.2.2:8000")) list.add("http://10.0.2.2:8000") // Emulator loopback
        return list
    }

    suspend fun testConnection(baseUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val targetUrl = if (cleanUrl.endsWith("/health") || cleanUrl.endsWith("/sessions")) cleanUrl else "$cleanUrl/"
        val request = Request.Builder().url(targetUrl).get().build()
        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val isSuccess = response.isSuccessful
            response.close()
            if (isSuccess) {
                Pair(true, "Connected successfully (HTTP $code)")
            } else {
                Pair(false, "Server returned HTTP $code")
            }
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Connection error")
        }
    }

    suspend fun syncPendingSessions(forceAll: Boolean = false): Int = withContext(Dispatchers.IO) {
        val pending = if (forceAll) {
            sessionRepository.getAllSessionsSync()
        } else {
            sessionRepository.getPendingSessions()
        }
        var syncedCount = 0
        val candidateUrls = getCandidateUrls()

        for (session in pending) {
            val reps = sessionRepository.getRepsForSessionSync(session.id)
            val repsJsonArray = JSONArray()

            for (r in reps) {
                val failureReasonsArray = try {
                    JSONArray(r.failureReasonsJson)
                } catch (_: Exception) {
                    JSONArray()
                }

                val repObj = JSONObject().apply {
                    put("rep_number", r.repNumber)
                    put("is_valid", r.isValid)
                    put("start_timestamp_ms", r.startTimestampMs)
                    put("peak_timestamp_ms", r.peakTimestampMs)
                    put("end_timestamp_ms", r.endTimestampMs)
                    put("duration_ms", r.durationMs)
                    put("peak_knee_angle", r.peakKneeAngle)
                    put("start_knee_angle", r.startKneeAngle)
                    put("end_knee_angle", r.endKneeAngle)
                    put("feedback_message", r.feedbackMessage)
                    put("failure_reasons", failureReasonsArray)
                }
                repsJsonArray.put(repObj)
            }

            val events = sessionRepository.getEventsForSessionSync(session.id)
            val eventsJsonArray = JSONArray()
            for (e in events) {
                val eventObj = JSONObject().apply {
                    put("id", e.id)
                    put("event_type", e.eventType)
                    put("start_time_ms", e.startTimeMs)
                    put("end_time_ms", e.endTimeMs)
                    put("duration_ms", e.durationMs)
                    put("frame_count", e.frameCount)
                    put("details", e.details)
                }
                eventsJsonArray.put(eventObj)
            }

            val jsonBody = JSONObject().apply {
                put("id", session.id)
                put("device_id", session.deviceId)
                put("exercise_name", session.exerciseName)
                put("started_at", session.startedAt)
                put("completed_at", session.completedAt)
                put("duration_seconds", session.durationSeconds)
                put("total_reps", session.totalReps)
                put("valid_reps", session.validReps)
                put("avg_peak_knee_angle", session.avgPeakKneeAngle)
                put("min_knee_angle", session.minKneeAngle)
                put("max_knee_angle", session.maxKneeAngle)
                put("evidence_failure_count", session.evidenceFailureCount)
                put("reps", repsJsonArray)
                put("evidence_events", eventsJsonArray)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            var sessionSynced = false

            // Try candidate endpoints until one succeeds
            for (baseUrl in candidateUrls) {
                val request = Request.Builder()
                    .url("$baseUrl/sessions")
                    .post(requestBody)
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        sessionRepository.markSessionSynced(session.id)
                        syncedCount++
                        sessionSynced = true
                        Log.d(TAG, "Successfully synced session ${session.id} to $baseUrl")
                        response.close()
                        break
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Sync to $baseUrl failed (${e.message}), trying next fallback...")
                }
            }

            if (!sessionSynced) {
                Log.i(TAG, "Session ${session.id} remains PENDING in Room database for next sync attempt.")
            }
        }

        syncedCount
    }
}
