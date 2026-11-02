package com.kinexmed.ui

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Mobile ergonomics helper providing tactile haptic feedback
 * and native spring scale-down (0.96 scale, 80-120ms release) per Layer 4 specification.
 */
object TouchErgonomicsHelper {

    /**
     * Attaches tactile haptic feedback and spring physics scale animation
     * on touch down and release, without interfering with accessibility or click dispatch.
     */
    fun attachSpringPress(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    v.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(80)
                        .start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Attaches spring press and click listener together.
     */
    fun attachSpringClickListener(view: View, onClick: View.OnClickListener) {
        attachSpringPress(view)
        view.setOnClickListener(onClick)
    }

    /**
     * Triggers confirmation haptic tick (e.g. rep completed or active toggle).
     */
    fun triggerConfirmTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /**
     * Triggers warning / violation double tick (e.g. evidence occlusion or insufficient depth).
     */
    fun triggerAlertTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

