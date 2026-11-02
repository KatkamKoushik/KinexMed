package com.kinexmed.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.kinexmed.domain.model.Landmark

/**
 * Encapsulates MediaPipe Pose Landmarker for real-time live streaming video inference.
 * Uses LIVE_STREAM mode to ensure zero blocking of the CameraX frame capture thread.
 */
class PoseLandmarkerHelper(
    val context: Context,
    val minPoseDetectionConfidence: Float = 0.5f,
    val minPoseTrackingConfidence: Float = 0.5f,
    val minPosePresenceConfidence: Float = 0.5f,
    val landmarkerListener: LandmarkerListener? = null
) {
    private var poseLandmarker: PoseLandmarker? = null
    private var isReady = false
    private var lastFrameTime = 0L

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
        const val MODEL_FULL = "pose_landmarker_full.task"
        const val MODEL_LITE = "pose_landmarker_lite.task"
    }

    init {
        setupPoseLandmarker()
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(
            resultBundle: ResultBundle
        )
    }

    data class ResultBundle(
        val domainLandmarks: Map<Int, Landmark>,
        val rawResults: PoseLandmarkerResult,
        val inferenceTimeMs: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    private fun setupPoseLandmarker() {
        // Try full model first; fallback to lite model
        val modelsToTry = listOf(MODEL_FULL, MODEL_LITE)
        var initialized = false

        for (modelName in modelsToTry) {
            try {
                Log.d(TAG, "Attempting to initialize MediaPipe PoseLandmarker with: $modelName")
                val baseOptionBuilder = BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath(modelName)

                val baseOptions = baseOptionBuilder.build()
                val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)

                val options = optionsBuilder.build()
                poseLandmarker = PoseLandmarker.createFromOptions(context, options)
                isReady = true
                initialized = true
                Log.d(TAG, "Successfully initialized MediaPipe PoseLandmarker with: $modelName")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Failed initializing MediaPipe with $modelName: ${e.message}", e)
            }
        }

        if (!initialized) {
            landmarkerListener?.onError("MediaPipe PoseLandmarker could not initialize any model asset.")
        }
    }

    /**
     * Sends camera bitmap asynchronously to MediaPipe.
     * Uses strictly monotonically increasing timestamp for LIVE_STREAM running mode.
     */
    fun detectLiveStream(bitmap: Bitmap) {
        if (!isReady || poseLandmarker == null) {
            return
        }

        var frameTime = SystemClock.uptimeMillis()
        if (frameTime <= lastFrameTime) {
            frameTime = lastFrameTime + 1
        }
        lastFrameTime = frameTime

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectAsync: ${e.message}", e)
            landmarkerListener?.onError("Detection error: ${e.localizedMessage}")
        }
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        val domainLandmarks = mutableMapOf<Int, Landmark>()
        val landmarksList = result.landmarks()

        if (landmarksList.isNotEmpty() && landmarksList[0].isNotEmpty()) {
            val pose = landmarksList[0]
            for (i in pose.indices) {
                val mpLm = pose[i]
                domainLandmarks[i] = Landmark(
                    id = i,
                    x = mpLm.x(),
                    y = mpLm.y(),
                    z = mpLm.z(),
                    visibility = if (mpLm.visibility().isPresent) mpLm.visibility().get() else 1.0f,
                    presence = if (mpLm.presence().isPresent) mpLm.presence().get() else 1.0f
                )
            }
        }

        landmarkerListener?.onResults(
            ResultBundle(
                domainLandmarks = domainLandmarks,
                rawResults = result,
                inferenceTimeMs = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        Log.e(TAG, "MediaPipe live stream callback error: ${error.message}", error)
        landmarkerListener?.onError(error.message ?: "Unknown MediaPipe live stream error")
    }

    fun clear() {
        isReady = false
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
