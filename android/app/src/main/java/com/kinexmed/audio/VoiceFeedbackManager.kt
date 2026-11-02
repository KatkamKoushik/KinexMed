package com.kinexmed.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Text-to-speech audio feedback manager.
 * Uses QUEUE_FLUSH to prevent audio buffer pileup, and enforces a smart de-duplication
 * policy so repositioning guidance is never repeated repeatedly like spam.
 */
class VoiceFeedbackManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var lastSpokenMessage = ""
    private var lastSpokenTimestampMs = 0L

    // Repetition throttle: Identical guidance messages will NOT repeat for at least 15 seconds
    private val guidanceRepeatCooldownMs = 15000L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.05f)
            isInitialized = true
        }
    }

    /**
     * Speaks guidance/repositioning messages (e.g. "Step back into view").
     * Throttles identical messages for at least 15 seconds to eliminate repetitive spam.
     */
    fun speakGuidance(message: String) {
        if (!isInitialized || message.isBlank()) return

        val now = System.currentTimeMillis()
        // If message is the same, stay silent until cooldown expires
        if (message == lastSpokenMessage && (now - lastSpokenTimestampMs) < guidanceRepeatCooldownMs) {
            return
        }

        lastSpokenMessage = message
        lastSpokenTimestampMs = now

        // Always QUEUE_FLUSH so old spoken cues are immediately replaced, never queued up
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guidance_${System.currentTimeMillis()}")
    }

    /**
     * Speaks high-priority event announcements (e.g. rep completion, depth feedback).
     */
    fun speakFeedback(message: String, isHighPriority: Boolean = false) {
        if (!isInitialized || message.isBlank()) return

        val now = System.currentTimeMillis()
        if (!isHighPriority && message == lastSpokenMessage && (now - lastSpokenTimestampMs) < guidanceRepeatCooldownMs) {
            return
        }

        lastSpokenMessage = message
        lastSpokenTimestampMs = now

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "feedback_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
