package com.kinexmed.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.kinexmed.domain.model.EvidenceStatus
import com.kinexmed.domain.model.Landmark
import com.kinexmed.domain.model.PoseLandmarks
import kotlin.math.min

/**
 * Custom View to draw real skeletal landmarks, connection bones,
 * and dynamic knee angle arc directly over the CameraX preview.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var landmarks: Map<Int, Landmark> = emptyMap()
    private var evidenceStatus: EvidenceStatus = EvidenceStatus.Sufficient
    private var currentKneeAngle: Double? = null

    private val pointPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright green
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val keyJointPaint = Paint().apply {
        color = Color.parseColor("#00B0FF") // Bright cyan
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bonePaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 200
    }

    private val angleTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private val warnPaint = Paint().apply {
        color = Color.parseColor("#FF9100") // Amber warning
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    fun updatePose(
        newLandmarks: Map<Int, Landmark>,
        status: EvidenceStatus,
        kneeAngle: Double?
    ) {
        this.landmarks = newLandmarks
        this.evidenceStatus = status
        this.currentKneeAngle = kneeAngle
        postInvalidate()
    }

    fun clear() {
        landmarks = emptyMap()
        evidenceStatus = EvidenceStatus.Sufficient
        currentKneeAngle = null
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (landmarks.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()

        val isSufficient = evidenceStatus is EvidenceStatus.Sufficient
        val activeBonePaint = if (isSufficient) bonePaint else warnPaint

        // Define skeletal connections
        val connections = listOf(
            // Left leg
            Pair(PoseLandmarks.LEFT_HIP, PoseLandmarks.LEFT_KNEE),
            Pair(PoseLandmarks.LEFT_KNEE, PoseLandmarks.LEFT_ANKLE),
            Pair(PoseLandmarks.LEFT_ANKLE, PoseLandmarks.LEFT_HEEL),
            // Right leg
            Pair(PoseLandmarks.RIGHT_HIP, PoseLandmarks.RIGHT_KNEE),
            Pair(PoseLandmarks.RIGHT_KNEE, PoseLandmarks.RIGHT_ANKLE),
            Pair(PoseLandmarks.RIGHT_ANKLE, PoseLandmarks.RIGHT_HEEL),
            // Pelvis
            Pair(PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP),
            // Torso
            Pair(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.RIGHT_SHOULDER),
            Pair(PoseLandmarks.LEFT_SHOULDER, PoseLandmarks.LEFT_HIP),
            Pair(PoseLandmarks.RIGHT_SHOULDER, PoseLandmarks.RIGHT_HIP)
        )

        // Draw bone lines
        for ((startId, endId) in connections) {
            val p1 = landmarks[startId]
            val p2 = landmarks[endId]
            if (p1 != null && p2 != null) {
                canvas.drawLine(p1.x * w, p1.y * h, p2.x * w, p2.y * h, activeBonePaint)
            }
        }

        // Draw landmark dots
        for ((id, lm) in landmarks) {
            val cx = lm.x * w
            val cy = lm.y * h
            val isKeyJoint = id in listOf(
                PoseLandmarks.LEFT_HIP, PoseLandmarks.RIGHT_HIP,
                PoseLandmarks.LEFT_KNEE, PoseLandmarks.RIGHT_KNEE,
                PoseLandmarks.LEFT_ANKLE, PoseLandmarks.RIGHT_ANKLE
            )

            if (isKeyJoint) {
                canvas.drawCircle(cx, cy, 14f, keyJointPaint)
                canvas.drawCircle(cx, cy, 8f, pointPaint)
            } else {
                canvas.drawCircle(cx, cy, 6f, pointPaint)
            }
        }

        // Draw knee angle readout near active knee
        currentKneeAngle?.let { angle ->
            val knee = landmarks[PoseLandmarks.LEFT_KNEE] ?: landmarks[PoseLandmarks.RIGHT_KNEE]
            knee?.let {
                val kx = it.x * w + 30f
                val ky = it.y * h
                canvas.drawText("${angle.toInt()}°", kx, ky, angleTextPaint)
            }
        }
    }
}
