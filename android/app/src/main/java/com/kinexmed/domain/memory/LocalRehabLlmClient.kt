package com.kinexmed.domain.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface LocalRehabLlmClient {
    suspend fun query(prompt: String, retrievedSnippet: String): String?
}

class HttpLocalRehabLlmClient(
    private val endpointUrl: String
) : LocalRehabLlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override suspend fun query(prompt: String, retrievedSnippet: String): String? = withContext(Dispatchers.IO) {
        if (endpointUrl.isBlank()) return@withContext null

        val systemPrompt = "You are KinexMed Personal Rehabilitation Assistant. You answer patient questions strictly and solely using the provided retrieved telemetry. You must NEVER invent measurements, NEVER diagnose conditions, NEVER prescribe medical treatment, and NEVER claim clinical cure. Keep answers concise, empathetic, and factual."
        val userContent = "Retrieved KinexMed Session Telemetry:\n$retrievedSnippet\n\nPatient Question: $prompt"

        val jsonBody = JSONObject().apply {
            put("model", "llama3.2")
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            put("messages", messages)
            put("temperature", 0.1)
            put("max_tokens", 256)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(endpointUrl)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            val respBody = response.body?.string() ?: ""
            response.close()

            val jsonResp = JSONObject(respBody)
            val choices = jsonResp.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                return@withContext message?.optString("content")?.trim()
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
