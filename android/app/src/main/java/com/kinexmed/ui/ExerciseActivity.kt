package com.kinexmed.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kinexmed.R
import com.kinexmed.audio.VoiceFeedbackManager
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.repository.SessionRepository
import com.kinexmed.domain.evidence.EvidenceEngine
import com.kinexmed.domain.filter.OneEuroFilter
import com.kinexmed.domain.fsm.ExerciseStateMachine
import com.kinexmed.domain.fsm.SquatStateMachine
import com.kinexmed.domain.geometry.GeometryEngine
import com.kinexmed.domain.model.*
import com.kinexmed.domain.registry.ExerciseRegistry
import com.kinexmed.mediapipe.PoseLandmarkerHelper
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Real-time exercise observation Activity for KinexMed.
 * Implements a strict, deterministic SessionLifecycleState machine:
 * IDLE -> PREPARING -> ACTIVE -> PAUSED -> ACTIVE -> FINISHING -> COMPLETED
 *
 * Invariants:
 * 1. Starting an exercise session requires an explicit tap on START SESSION.
 * 2. Finishing requires an explicit confirmation dialog showing recorded reps.
 * 3. Double-tap debouncing prevents duplicate finishing or restarts.
 * 4. Active session timer measures elapsed active exercise duration, decoupled from FSM state.
 * 5. Pose loss does NOT terminate sessions or discard accumulated reps.
 * 6. After session completion, the app NEVER auto-restarts without explicit user action.
 */
class ExerciseActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var tvStateBadge: TextView
    private lateinit var tvEvidenceMessage: TextView
    private lateinit var tvValidReps: TextView
    private lateinit var tvTotalReps: TextView
    private lateinit var tvCurrentAngle: TextView
    private lateinit var tvSessionTimer: TextView
    private lateinit var tvGuidanceMessage: TextView
    private lateinit var cardEvidenceStatus: CardView

    // Lifecycle Controls
    private lateinit var btnStartSession: Button
    private lateinit var layoutActiveControls: View
    private lateinit var btnPauseSession: Button
    private lateinit var btnFinishSession: Button
    private lateinit var btnCancelSession: Button

    // Paused Overlay
    private lateinit var pausedOverlay: FrameLayout
    private lateinit var btnResumeOverlay: Button

    // Completion Overlay
    private lateinit var completionOverlay: FrameLayout
    private lateinit var tvCompletionValidReps: TextView
    private lateinit var tvCompletionTotalReps: TextView
    private lateinit var tvCompletionDuration: TextView
    private lateinit var tvCompletionAvgRom: TextView
    private lateinit var tvCompletionSyncStatus: TextView
    private lateinit var btnStartNewSessionOverlay: Button
    private lateinit var btnViewHistoryOverlay: Button
    private lateinit var btnExitHomeOverlay: Button

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private lateinit var voiceFeedback: VoiceFeedbackManager
    private lateinit var sessionRepository: SessionRepository
    private lateinit var syncManager: com.kinexmed.sync.SyncManager

    companion object {
        const val EXTRA_EXERCISE_ID = "extra_exercise_id"
    }

    // Pipeline Domain Components
    private val evidenceEngine = EvidenceEngine()
    private val angleFilter = OneEuroFilter(minCutoff = 1.2, beta = 0.05)
    private lateinit var exerciseDef: ExerciseDefinition
    private lateinit var exerciseFsm: ExerciseStateMachine

    private lateinit var tvExerciseTitle: TextView
    private lateinit var tvMetricTitle: TextView
    private lateinit var tvTargetDepth: TextView

    // Explicit Session Lifecycle State
    private var sessionLifecycleState = SessionLifecycleState.IDLE
    private var currentSessionId = UUID.randomUUID().toString()
    private var sessionStartTimeMs = 0L
    private var pausedAtMs = 0L
    private var totalPausedDurationMs = 0L
    private var lastFinishClickTimeMs = 0L

    // Hands-Free Auto-Countdown State
    private var idleEvidenceStartTimeMs = 0L
    private var countdownJob: Job? = null
    private val appSupervisorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-session Kinematic Data
    private val sessionReps = mutableListOf<RepRecord>()
    private val evidenceEpisodes = mutableListOf<EvidenceEpisode>()
    private var currentEvidenceEpisode: EvidenceEpisode? = null
    private var minAngleSeenOverall = 180.0
    private var maxAngleSeenOverall = 0.0
    private var lastKnownAngle = 180.0

    private var timerJob: Job? = null

    // Performance & Latency Instrumentation (CPU Delegate)
    private var perfFrameCount = 0
    private var perfLastFpsTimestamp = 0L
    private var perfTotalInferenceTimeMs = 0L

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required for real-time exercise observation", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        val exerciseId = intent.getStringExtra(EXTRA_EXERCISE_ID) ?: "squat"
        exerciseDef = ExerciseRegistry.getById(exerciseId)
        exerciseFsm = ExerciseRegistry.createStateMachine(exerciseDef.type)

        initViews()
        initServices()
        updateUiForLifecycleState()
        startTimerLoop()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleUserBackNavigation()
            }
        })
    }

    private fun initViews() {
        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        tvStateBadge = findViewById(R.id.tvStateBadge)
        tvEvidenceMessage = findViewById(R.id.tvEvidenceMessage)
        tvValidReps = findViewById(R.id.tvValidReps)
        tvTotalReps = findViewById(R.id.tvTotalReps)
        tvCurrentAngle = findViewById(R.id.tvCurrentAngle)
        tvSessionTimer = findViewById(R.id.tvSessionTimer)
        tvGuidanceMessage = findViewById(R.id.tvGuidanceMessage)
        cardEvidenceStatus = findViewById(R.id.cardEvidenceStatus)
        tvExerciseTitle = findViewById(R.id.tvExerciseTitle)
        tvMetricTitle = findViewById(R.id.tvMetricTitle)
        tvTargetDepth = findViewById(R.id.tvTargetDepth)

        tvExerciseTitle.text = "KinexMed : ${exerciseDef.displayName}"
        tvMetricTitle.text = exerciseFsm.primaryMetricName.uppercase()
        tvTargetDepth.text = exerciseFsm.targetThresholdDesc

        btnStartSession = findViewById(R.id.btnStartSession)
        layoutActiveControls = findViewById(R.id.layoutActiveControls)
        btnPauseSession = findViewById(R.id.btnPauseSession)
        btnFinishSession = findViewById(R.id.btnFinishSession)
        btnCancelSession = findViewById(R.id.btnCancelSession)

        pausedOverlay = findViewById(R.id.pausedOverlay)
        btnResumeOverlay = findViewById(R.id.btnResumeOverlay)

        completionOverlay = findViewById(R.id.completionOverlay)
        tvCompletionValidReps = findViewById(R.id.tvCompletionValidReps)
        tvCompletionTotalReps = findViewById(R.id.tvCompletionTotalReps)
        tvCompletionDuration = findViewById(R.id.tvCompletionDuration)
        tvCompletionAvgRom = findViewById(R.id.tvCompletionAvgRom)
        tvCompletionSyncStatus = findViewById(R.id.tvCompletionSyncStatus)
        btnStartNewSessionOverlay = findViewById(R.id.btnStartNewSessionOverlay)
        btnViewHistoryOverlay = findViewById(R.id.btnViewHistoryOverlay)
        btnExitHomeOverlay = findViewById(R.id.btnExitHomeOverlay)

        // Button Listeners
        btnStartSession.setOnClickListener {
            startNewSession()
        }

        btnPauseSession.setOnClickListener {
            togglePauseSession()
        }

        btnResumeOverlay.setOnClickListener {
            resumeSession()
        }

        btnFinishSession.setOnClickListener {
            onFinishSessionClicked()
        }

        btnCancelSession.setOnClickListener {
            confirmCancelSession()
        }

        btnStartNewSessionOverlay.setOnClickListener {
            startNewSession()
        }

        btnViewHistoryOverlay.setOnClickListener {
            val intent = Intent(this, SessionHistoryActivity::class.java)
            startActivity(intent)
            finish()
        }

        btnExitHomeOverlay.setOnClickListener {
            finish()
        }
    }

    private fun initServices() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        voiceFeedback = VoiceFeedbackManager(this)

        val db = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(db.sessionDao(), db.repDao(), db.evidenceEventDao())
        syncManager = com.kinexmed.sync.SyncManager(sessionRepository, this)

        // Attach structured debug logger if squat
        (exerciseFsm as? SquatStateMachine)?.debugLogger = { msg ->
            android.util.Log.d("SquatFSM", msg)
        }

        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            landmarkerListener = this
        )
    }

    /**
     * Updates all UI elements, overlays, and button states to match the session lifecycle.
     */
    private fun updateUiForLifecycleState() {
        when (sessionLifecycleState) {
            SessionLifecycleState.IDLE -> {
                btnStartSession.visibility = View.VISIBLE
                btnStartSession.isEnabled = true
                btnStartSession.text = "START SESSION"
                layoutActiveControls.visibility = View.GONE
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.GONE

                tvStateBadge.text = "IDLE"
                tvValidReps.text = "0"
                tvTotalReps.text = "Total: 0"
                tvCurrentAngle.text = "--${exerciseFsm.primaryMetricUnit}"
                tvSessionTimer.text = "00:00"
                tvGuidanceMessage.text = "Place phone at ${exerciseDef.cameraPlacement}, step back ${exerciseDef.cameraDistance}, then press START SESSION."
                tvMetricTitle.text = exerciseFsm.primaryMetricName.uppercase()
                tvTargetDepth.text = exerciseFsm.targetThresholdDesc
            }

            SessionLifecycleState.PREPARING -> {
                btnStartSession.visibility = View.VISIBLE
                btnStartSession.isEnabled = false
                btnStartSession.text = "PREPARING..."
                layoutActiveControls.visibility = View.GONE
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.GONE
            }

            SessionLifecycleState.ACTIVE -> {
                btnStartSession.visibility = View.GONE
                layoutActiveControls.visibility = View.VISIBLE
                btnPauseSession.text = "Pause"
                btnPauseSession.isEnabled = true
                val validCount = sessionReps.count { it.isValid }
                btnFinishSession.text = if (validCount > 0) "Finish Session ($validCount Reps)" else "Finish Session"
                btnFinishSession.isEnabled = true
                btnCancelSession.isEnabled = true
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.GONE
            }

            SessionLifecycleState.PAUSED -> {
                btnStartSession.visibility = View.GONE
                layoutActiveControls.visibility = View.VISIBLE
                btnPauseSession.text = "Resume"
                btnPauseSession.isEnabled = true
                btnFinishSession.text = "Finish Session"
                btnFinishSession.isEnabled = true
                btnCancelSession.isEnabled = true
                pausedOverlay.visibility = View.VISIBLE
                completionOverlay.visibility = View.GONE
            }

            SessionLifecycleState.FINISHING -> {
                btnStartSession.visibility = View.GONE
                layoutActiveControls.visibility = View.VISIBLE
                btnFinishSession.text = "Saving..."
                btnFinishSession.isEnabled = false
                btnPauseSession.isEnabled = false
                btnCancelSession.isEnabled = false
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.GONE
                tvGuidanceMessage.text = "Saving session to local database..."
            }

            SessionLifecycleState.COMPLETED -> {
                btnStartSession.visibility = View.GONE
                layoutActiveControls.visibility = View.GONE
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.VISIBLE
                tvStateBadge.text = "COMPLETED"
            }

            SessionLifecycleState.CANCELLED -> {
                btnStartSession.visibility = View.VISIBLE
                layoutActiveControls.visibility = View.GONE
                pausedOverlay.visibility = View.GONE
                completionOverlay.visibility = View.GONE
                tvStateBadge.text = "CANCELLED"
                tvGuidanceMessage.text = "Session cancelled."
            }
        }
    }

    /**
     * Explicit user action or hands-free auto-trigger: starts a brand new exercise session.
     * Generates a new unique session ID, resets the timer, FSM, and in-memory reps.
     */
    private fun startNewSession() {
        countdownJob?.cancel()
        countdownJob = null
        idleEvidenceStartTimeMs = 0L

        GeometryEngine.resetSidePreferences()
        currentSessionId = UUID.randomUUID().toString()
        sessionStartTimeMs = System.currentTimeMillis()
        pausedAtMs = 0L
        totalPausedDurationMs = 0L
        sessionReps.clear()
        evidenceEpisodes.clear()
        currentEvidenceEpisode = null
        minAngleSeenOverall = 180.0
        maxAngleSeenOverall = 0.0
        lastKnownAngle = 180.0

        exerciseFsm.reset()
        evidenceEngine.reset()
        angleFilter.reset()

        sessionLifecycleState = SessionLifecycleState.ACTIVE
        updateUiForLifecycleState()

        val prompt = "${exerciseDef.displayName} session started. ${exerciseDef.instructions.firstOrNull() ?: "Assume starting posture."}"
        voiceFeedback.speakFeedback(prompt, isHighPriority = true)
        android.util.Log.i("KinexMedSession", "Explicit session start: ID=$currentSessionId, Exercise=${exerciseDef.type.id}")
    }

    private fun triggerHandsFreeCountdown() {
        if (sessionLifecycleState != SessionLifecycleState.IDLE) return
        sessionLifecycleState = SessionLifecycleState.PREPARING
        updateUiForLifecycleState()

        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            voiceFeedback.speakFeedback("Framing verified. Starting in 5 seconds.", isHighPriority = true)
            for (sec in 5 downTo 1) {
                if (sessionLifecycleState != SessionLifecycleState.PREPARING) return@launch
                tvEvidenceMessage.text = "Hands-free countdown: $sec..."
                tvGuidanceMessage.text = "Starting in $sec seconds"
                voiceFeedback.speakFeedback("$sec", isHighPriority = true)
                delay(1000)
            }
            if (sessionLifecycleState == SessionLifecycleState.PREPARING) {
                voiceFeedback.speakFeedback("Begin!", isHighPriority = true)
                startNewSession()
            }
        }
    }

    /**
     * Monotonic active session timer.
     * Computes elapsed time purely from sessionStartTimeMs and total paused duration.
     * Decoupled from FSM states, pose loss, and frame processing.
     */
    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                delay(500)
                if (sessionLifecycleState == SessionLifecycleState.ACTIVE && sessionStartTimeMs > 0L) {
                    val now = System.currentTimeMillis()
                    val activeMs = (now - sessionStartTimeMs - totalPausedDurationMs).coerceAtLeast(0L)
                    val activeSeconds = activeMs / 1000
                    val minutes = activeSeconds / 60
                    val seconds = activeSeconds % 60
                    tvSessionTimer.text = String.format("%02d:%02d", minutes, seconds)
                }
            }
        }
    }

    private fun togglePauseSession() {
        if (sessionLifecycleState == SessionLifecycleState.ACTIVE) {
            pauseSession()
        } else if (sessionLifecycleState == SessionLifecycleState.PAUSED) {
            resumeSession()
        }
    }

    private fun pauseSession() {
        if (sessionLifecycleState != SessionLifecycleState.ACTIVE) return
        sessionLifecycleState = SessionLifecycleState.PAUSED
        pausedAtMs = System.currentTimeMillis()
        updateUiForLifecycleState()
        voiceFeedback.speakFeedback("Exercise paused", isHighPriority = true)
    }

    private fun resumeSession() {
        if (sessionLifecycleState != SessionLifecycleState.PAUSED) return
        val now = System.currentTimeMillis()
        if (pausedAtMs > 0L) {
            totalPausedDurationMs += (now - pausedAtMs)
            pausedAtMs = 0L
        }
        sessionLifecycleState = SessionLifecycleState.ACTIVE
        updateUiForLifecycleState()
        voiceFeedback.speakFeedback("Exercise resumed", isHighPriority = true)
    }

    private fun handleUserBackNavigation() {
        if (sessionLifecycleState == SessionLifecycleState.ACTIVE || sessionLifecycleState == SessionLifecycleState.PAUSED) {
            if (sessionReps.isNotEmpty()) {
                val validReps = sessionReps.count { it.isValid }
                val totalReps = sessionReps.size
                AlertDialog.Builder(this)
                    .setTitle("Finish and Save Session?")
                    .setMessage("You have completed $validReps valid reps ($totalReps attempted). Would you like to finish and save this session?")
                    .setPositiveButton("Finish & Save") { _, _ ->
                        executeFinishingPipeline()
                    }
                    .setNegativeButton("Discard") { _, _ ->
                        sessionLifecycleState = SessionLifecycleState.CANCELLED
                        sessionReps.clear()
                        exerciseFsm.reset()
                        finish()
                    }
                    .setNeutralButton("Keep Exercising", null)
                    .show()
                return
            } else {
                confirmCancelSession()
                return
            }
        }
        finish()
    }

    /**
     * Finish button click handler.
     * Debounces rapid double-clicks and shows an explicit confirmation dialog.
     */
    private fun onFinishSessionClicked() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFinishClickTimeMs < 800L) {
            // Debounce rapid double-clicks
            return
        }
        lastFinishClickTimeMs = now

        // Finish Session is ONLY permitted when ACTIVE or PAUSED
        if (sessionLifecycleState != SessionLifecycleState.ACTIVE && sessionLifecycleState != SessionLifecycleState.PAUSED) {
            return
        }

        val validReps = sessionReps.count { it.isValid }
        val totalReps = sessionReps.size

        AlertDialog.Builder(this)
            .setTitle("Finish this session?")
            .setMessage("$validReps valid reps recorded ($totalReps attempted).")
            .setPositiveButton("Finish Session") { _, _ ->
                executeFinishingPipeline()
            }
            .setNegativeButton("Keep Exercising", null)
            .show()
    }

    /**
     * Confirms before cancelling an active exercise session.
     */
    private fun confirmCancelSession() {
        if (sessionLifecycleState == SessionLifecycleState.IDLE) {
            finish()
            return
        }

        if (sessionLifecycleState != SessionLifecycleState.ACTIVE && sessionLifecycleState != SessionLifecycleState.PAUSED) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Cancel Session?")
            .setMessage("Are you sure you want to cancel this exercise session? Progress will be discarded without saving.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                sessionLifecycleState = SessionLifecycleState.CANCELLED
                sessionReps.clear()
                exerciseFsm.reset()
                updateUiForLifecycleState()
                finish()
            }
            .setNegativeButton("Keep Exercising", null)
            .show()
    }

    /**
     * Executes the finishing pipeline:
     * 1. Gates state to FINISHING (stops new frames and disables buttons)
     * 2. Calculates accurate active duration
     * 3. Saves session to Room SQLite
     * 4. Attempts sync to FastAPI backend
     * 5. Enters COMPLETED state and displays completion overlay
     */
    private fun executeFinishingPipeline() {
        if (sessionLifecycleState == SessionLifecycleState.FINISHING || sessionLifecycleState == SessionLifecycleState.COMPLETED) {
            return
        }
        sessionLifecycleState = SessionLifecycleState.FINISHING
        updateUiForLifecycleState()

        val completedAt = System.currentTimeMillis()
        if (pausedAtMs > 0L) {
            totalPausedDurationMs += (completedAt - pausedAtMs)
            pausedAtMs = 0L
        }

        if (currentEvidenceEpisode != null) {
            evidenceEpisodes.add(currentEvidenceEpisode!!)
            currentEvidenceEpisode = null
        }

        val rawDurationMs = (completedAt - sessionStartTimeMs - totalPausedDurationMs).coerceAtLeast(0L)
        val durationSeconds = (rawDurationMs / 1000.0).coerceAtLeast(1.0)
        val validReps = sessionReps.count { it.isValid }
        val totalReps = sessionReps.size
        val warningEpisodeCount = evidenceEpisodes.size

        val avgPeak = if (sessionReps.isNotEmpty()) {
            sessionReps.map { it.peakKneeAngle }.average()
        } else {
            0.0
        }

        val summary = SessionSummary(
            sessionId = currentSessionId,
            exerciseName = exerciseDef.type.id,
            startedAt = sessionStartTimeMs,
            completedAt = completedAt,
            durationSeconds = durationSeconds,
            totalReps = totalReps,
            validReps = validReps,
            avgPeakKneeAngle = avgPeak,
            minKneeAngle = if (minAngleSeenOverall <= 180.0) minAngleSeenOverall else 0.0,
            maxKneeAngle = maxAngleSeenOverall,
            evidenceFailureCount = warningEpisodeCount,
            reps = sessionReps.toList(),
            evidenceEpisodes = evidenceEpisodes.toList()
        )

        appSupervisorScope.launch {
            val (_, syncedCount) = withContext(Dispatchers.IO) {
                val entity = sessionRepository.recordCompletedSession(summary)
                val synced = syncManager.syncPendingSessions()
                Pair(entity, synced)
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    sessionLifecycleState = SessionLifecycleState.COMPLETED
                    updateUiForLifecycleState()

                    val syncStatusText = if (syncedCount > 0) {
                        "Synchronized to Clinician Dashboard"
                    } else {
                        "Saved locally in Room SQLite (sync pending)"
                    }

                    // Populate completion overlay statistics
                    tvCompletionValidReps.text = "$validReps"
                    tvCompletionTotalReps.text = "Total Reps: $totalReps"
                    val minutes = durationSeconds.toInt() / 60
                    val seconds = durationSeconds.toInt() % 60
                    tvCompletionDuration.text = String.format("%02d:%02d", minutes, seconds)
                    tvCompletionAvgRom.text = "${avgPeak.toInt()}°"
                    tvCompletionSyncStatus.text = syncStatusText

                    voiceFeedback.speakFeedback("Session complete. $validReps valid reps recorded.", isHighPriority = true)
                }
                android.util.Log.i("KinexMedSession", "Session finished & saved: ID=$currentSessionId, valid=$validReps/$totalReps")
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
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
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processCameraImage(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        } catch (exc: Exception) {
            Toast.makeText(this, "Failed to bind camera: ${exc.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processCameraImage(imageProxy: ImageProxy) {
        // Drop camera frame processing when session is paused or finishing/completed
        if (sessionLifecycleState == SessionLifecycleState.PAUSED ||
            sessionLifecycleState == SessionLifecycleState.FINISHING ||
            sessionLifecycleState == SessionLifecycleState.COMPLETED
        ) {
            imageProxy.close()
            return
        }

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

        imageProxy.close()
        poseLandmarkerHelper.detectLiveStream(rotatedBitmap)
    }

    /**
     * MediaPipe result callback invoked asynchronously.
     */
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // 1. Performance benchmarking instrumentation (MediaPipe CPU Delegate)
        perfFrameCount++
        perfTotalInferenceTimeMs += resultBundle.inferenceTimeMs
        val perfNow = SystemClock.elapsedRealtime()
        if (perfLastFpsTimestamp == 0L) {
            perfLastFpsTimestamp = perfNow
        } else if (perfNow - perfLastFpsTimestamp >= 2000L) {
            val elapsedSec = (perfNow - perfLastFpsTimestamp) / 1000.0
            val fps = perfFrameCount / elapsedSec
            val avgLatency = perfTotalInferenceTimeMs.toDouble() / perfFrameCount
            android.util.Log.i("KinexMedPerf", String.format("Performance: %.1f FPS, Avg Latency: %.1f ms (MediaPipe CPU)", fps, avgLatency))
            perfFrameCount = 0
            perfTotalInferenceTimeMs = 0L
            perfLastFpsTimestamp = perfNow
        }

        val timestamp = System.currentTimeMillis()
        val landmarks = resultBundle.domainLandmarks

        // 2. Gate by SessionLifecycleState
        if (sessionLifecycleState != SessionLifecycleState.ACTIVE) {
            val evidenceStatus = evidenceEngine.evaluateEvidence(landmarks, timestamp, exerciseDef.type)
            runOnUiThread {
                overlayView.updatePose(landmarks, evidenceStatus, null)

                when (sessionLifecycleState) {
                    SessionLifecycleState.IDLE -> {
                        if (evidenceStatus is EvidenceStatus.Sufficient) {
                            if (idleEvidenceStartTimeMs == 0L) {
                                idleEvidenceStartTimeMs = timestamp
                            } else if (timestamp - idleEvidenceStartTimeMs >= 1500L && countdownJob == null) {
                                triggerHandsFreeCountdown()
                            }
                            tvEvidenceMessage.text = "Body in frame! Hold still for hands-free start or tap START SESSION."
                        } else {
                            idleEvidenceStartTimeMs = 0L
                            tvEvidenceMessage.text = (evidenceStatus as EvidenceStatus.Insufficient).userMessage
                        }
                    }
                    SessionLifecycleState.PREPARING -> {
                        if (evidenceStatus is EvidenceStatus.Insufficient) {
                            // Evidence lost during countdown: reset
                            countdownJob?.cancel()
                            countdownJob = null
                            sessionLifecycleState = SessionLifecycleState.IDLE
                            idleEvidenceStartTimeMs = 0L
                            updateUiForLifecycleState()
                            tvEvidenceMessage.text = evidenceStatus.userMessage
                            voiceFeedback.speakGuidance(evidenceStatus.userMessage)
                        }
                    }
                    else -> {}
                }
            }
            return
        }

        // 3. Evidence Check during ACTIVE exercise
        val evidenceStatus = evidenceEngine.evaluateEvidence(landmarks, timestamp, exerciseDef.type)

        runOnUiThread {
            if (sessionLifecycleState != SessionLifecycleState.ACTIVE) return@runOnUiThread

            if (evidenceStatus is EvidenceStatus.Insufficient) {
                val failureReason = evidenceStatus.reason
                val episode = currentEvidenceEpisode
                if (episode != null && episode.reason == failureReason && (timestamp - episode.endTimeMs) < 1000L) {
                    episode.endTimeMs = timestamp
                    episode.frameCount++
                } else {
                    if (episode != null) {
                        evidenceEpisodes.add(episode)
                    }
                    currentEvidenceEpisode = EvidenceEpisode(
                        reason = failureReason,
                        startTimeMs = timestamp,
                        endTimeMs = timestamp,
                        frameCount = 1
                    )
                }

                tvEvidenceMessage.text = evidenceStatus.userMessage
                cardEvidenceStatus.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                tvGuidanceMessage.text = evidenceStatus.userMessage
                voiceFeedback.speakGuidance(evidenceStatus.userMessage)
                overlayView.updatePose(landmarks, evidenceStatus, null)

                // Pass into FSM so timeout/grace periods are maintained deterministically.
                // Notice: Existing session and rep records are NEVER reset on temporary pose loss!
                val fsmResult = exerciseFsm.update(landmarks, lastKnownAngle, timestamp, evidenceStatus)
                tvStateBadge.text = fsmResult.state.name
                return@runOnUiThread
            }

            // Evidence is sufficient: finalize any active failure episode
            if (currentEvidenceEpisode != null) {
                evidenceEpisodes.add(currentEvidenceEpisode!!)
                currentEvidenceEpisode = null
            }

            tvEvidenceMessage.text = "Good visual evidence. Tracking active."
            cardEvidenceStatus.setCardBackgroundColor(0xCC111827.toInt())

            // 4. Deterministic Exercise Joint Metric Calculation via Registry
            val rawAngle = ExerciseRegistry.extractMetric(exerciseDef.type, landmarks, timestamp)
            if (rawAngle == null) {
                overlayView.updatePose(landmarks, evidenceStatus, null)
                return@runOnUiThread
            }

            // 5. Temporal Smoothing
            val smoothedAngle = angleFilter.filter(rawAngle.angleDegrees, timestamp)
            lastKnownAngle = smoothedAngle

            if (smoothedAngle < minAngleSeenOverall) minAngleSeenOverall = smoothedAngle
            if (smoothedAngle > maxAngleSeenOverall) maxAngleSeenOverall = smoothedAngle

            // 6. Exercise State Machine & Rule Validation
            val fsmResult = exerciseFsm.update(landmarks, smoothedAngle, timestamp, evidenceStatus)

            // Update Overlay Canvas
            overlayView.updatePose(landmarks, evidenceStatus, smoothedAngle)

            // Update UI State Badge & Metrics
            tvStateBadge.text = fsmResult.state.name
            tvCurrentAngle.text = "${smoothedAngle.toInt()}${exerciseFsm.primaryMetricUnit}"
            tvValidReps.text = "${fsmResult.validReps}"
            tvTotalReps.text = "Total: ${fsmResult.totalReps}"
            tvGuidanceMessage.text = fsmResult.feedbackMessage

            // Handle Completed Repetition
            fsmResult.completedRep?.let { rep ->
                sessionReps.add(rep)
                voiceFeedback.speakFeedback(rep.feedbackMessage, isHighPriority = true)
                val validCount = sessionReps.count { it.isValid }
                btnFinishSession.text = "Finish Session ($validCount Reps)"
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            tvEvidenceMessage.text = "Sensor error: $error"
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing && (sessionLifecycleState == SessionLifecycleState.ACTIVE || sessionLifecycleState == SessionLifecycleState.PAUSED) && sessionReps.isNotEmpty()) {
            executeFinishingPipeline()
            return
        }
        if (sessionLifecycleState == SessionLifecycleState.ACTIVE) {
            pauseSession()
            android.util.Log.i("KinexMedLifecycle", "Exercise auto-paused on background transition")
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure paused state UI remains consistent when foregrounded
        if (sessionLifecycleState == SessionLifecycleState.PAUSED) {
            updateUiForLifecycleState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        poseLandmarkerHelper.clear()
        voiceFeedback.shutdown()
        cameraExecutor.shutdown()
    }
}
