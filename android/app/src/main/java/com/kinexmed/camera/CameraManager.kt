package com.kinexmed.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Encapsulates CameraX lifecycle, surface binding, and frame buffer extraction.
 * Decouples camera hardware lifecycle from ExerciseActivity UI rendering.
 */
class CameraManager(
    private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        viewFinder: PreviewView,
        onFrameAvailable: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner, viewFinder, onFrameAvailable, onError)
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        viewFinder: PreviewView,
        onFrameAvailable: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy, onFrameAvailable)
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun processImageProxy(
        imageProxy: ImageProxy,
        onFrameAvailable: (Bitmap) -> Unit
    ) {
        try {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                matrix,
                true
            )
            onFrameAvailable(rotatedBitmap)
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        } catch (_: Exception) {}
    }
}

