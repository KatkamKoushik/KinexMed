package com.kinexmed.domain.memory

import org.json.JSONArray
import org.json.JSONObject

data class StructuredEvidenceCard(
    val sessionId: String,
    val exerciseName: String,
    val repNumber: Int,
    val isValid: Boolean,
    val measuredAngle: Double,
    val targetAngle: Double,
    val unit: String = "°",
    val rejectionReason: String,
    val timestamp: Long,
    val evidenceImagePath: String? = null,
    val videoRecordingPath: String? = null,
    val videoTimestampMs: Long? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("exerciseName", exerciseName)
            put("repNumber", repNumber)
            put("isValid", isValid)
            put("measuredAngle", measuredAngle)
            put("targetAngle", targetAngle)
            put("unit", unit)
            put("rejectionReason", rejectionReason)
            put("timestamp", timestamp)
            put("evidenceImagePath", evidenceImagePath ?: JSONObject.NULL)
            put("videoRecordingPath", videoRecordingPath ?: JSONObject.NULL)
            put("videoTimestampMs", videoTimestampMs ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): StructuredEvidenceCard {
            return StructuredEvidenceCard(
                sessionId = json.optString("sessionId", ""),
                exerciseName = json.optString("exerciseName", "Exercise"),
                repNumber = json.optInt("repNumber", 1),
                isValid = json.optBoolean("isValid", false),
                measuredAngle = json.optDouble("measuredAngle", 0.0),
                targetAngle = json.optDouble("targetAngle", 0.0),
                unit = json.optString("unit", "°"),
                rejectionReason = json.optString("rejectionReason", ""),
                timestamp = json.optLong("timestamp", 0L),
                evidenceImagePath = if (json.isNull("evidenceImagePath")) null else json.optString("evidenceImagePath"),
                videoRecordingPath = if (json.isNull("videoRecordingPath")) null else json.optString("videoRecordingPath"),
                videoTimestampMs = if (json.isNull("videoTimestampMs")) null else json.optLong("videoTimestampMs")
            )
        }

        fun listToJson(cards: List<StructuredEvidenceCard>): String {
            val arr = JSONArray()
            for (c in cards) {
                arr.put(c.toJson())
            }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<StructuredEvidenceCard> {
            val list = mutableListOf<StructuredEvidenceCard>()
            if (jsonStr.isBlank()) return list
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    list.add(fromJson(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
            return list
        }
    }
}
