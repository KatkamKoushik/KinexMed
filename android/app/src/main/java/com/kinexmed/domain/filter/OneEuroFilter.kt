package com.kinexmed.domain.filter

import kotlin.math.abs
import kotlin.math.PI

/**
 * Low-pass filter component used internally by OneEuroFilter.
 */
class LowPassFilter(private var alpha: Double = 0.5) {
    private var y: Double? = null
    private var s: Double? = null

    fun filter(value: Double, alpha: Double = this.alpha): Double {
        this.alpha = alpha
        val result = if (s == null) {
            value
        } else {
            alpha * value + (1.0 - alpha) * s!!
        }
        y = value
        s = result
        return result
    }

    fun lastValue(): Double? = s

    fun reset() {
        y = null
        s = null
    }
}

/**
 * 1€ (One Euro) Filter for temporal smoothing of joint angles and landmark coordinates.
 * Solves jitter vs. lag tradeoff: heavy smoothing at low velocity, minimal lag at high velocity.
 *
 * References:
 * Casiez, G., Roussel, N., & Vogel, D. (2012). 1€ filter: a simple speed-based low-pass filter
 * for noisy input in interactive systems. In Proc. CHI 2012.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,  // Lower values decrease jitter during static posture
    private val beta: Double = 0.007,      // Higher values reduce lag during rapid movement
    private val dCutoff: Double = 1.0     // Cutoff for derivative computation
) {
    private val xFilter = LowPassFilter()
    private val dxFilter = LowPassFilter()
    private var lastTimestampSec: Double? = null

    private fun alpha(rate: Double, cutoff: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        val te = 1.0 / rate
        return 1.0 / (1.0 + tau / te)
    }

    /**
     * Filters input value with associated timestamp in milliseconds.
     */
    fun filter(value: Double, timestampMs: Long): Double {
        val tSec = timestampMs / 1000.0

        if (lastTimestampSec == null || lastTimestampSec == tSec) {
            lastTimestampSec = tSec
            return xFilter.filter(value, 1.0)
        }

        val dt = tSec - lastTimestampSec!!
        lastTimestampSec = tSec

        // Handle edge case of duplicate or inverted timestamps
        if (dt <= 0.0) {
            return xFilter.lastValue() ?: value
        }

        val rate = 1.0 / dt

        // Estimate derivative (velocity)
        val prevX = xFilter.lastValue() ?: value
        val dx = (value - prevX) * rate
        val edx = dxFilter.filter(dx, alpha(rate, dCutoff))

        // Dynamic cutoff based on velocity
        val cutoff = minCutoff + beta * abs(edx)

        return xFilter.filter(value, alpha(rate, cutoff))
    }

    fun reset() {
        xFilter.reset()
        dxFilter.reset()
        lastTimestampSec = null
    }
}
