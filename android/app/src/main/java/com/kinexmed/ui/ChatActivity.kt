package com.kinexmed.ui

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.entity.ChatMessageEntity
import com.kinexmed.data.entity.RepEntity
import com.kinexmed.data.repository.SessionRepository
import com.kinexmed.domain.memory.HttpLocalRehabLlmClient
import com.kinexmed.domain.memory.RehabAssistantCoordinator
import com.kinexmed.domain.memory.StructuredEvidenceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private lateinit var btnBackChat: ImageButton
    private lateinit var btnClearChat: ImageButton
    private lateinit var rvChatMessages: RecyclerView
    private lateinit var layoutChatEmptyState: LinearLayout
    private lateinit var etChatInput: EditText
    private lateinit var btnSendMessage: Button

    private lateinit var chipWhyRep7: Button
    private lateinit var chipRejectedYesterday: Button
    private lateinit var chipValidThisWeek: Button
    private lateinit var chipSummaryMonth: Button
    private lateinit var chipWhichExercise: Button
    private lateinit var chipExplainToday: Button
    private lateinit var chipLastSession: Button

    private lateinit var sessionRepository: SessionRepository
    private lateinit var coordinator: RehabAssistantCoordinator
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initDependencies()
        initViews()
        observeChatHistory()
    }

    private fun initDependencies() {
        val db = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(
            sessionDao = db.sessionDao(),
            repDao = db.repDao(),
            evidenceEventDao = db.evidenceEventDao(),
            chatMessageDao = db.chatMessageDao()
        )

        val prefs = getSharedPreferences("kinexmed_settings", Context.MODE_PRIVATE)
        val localLlmUrl = prefs.getString("pref_local_llm_url", "") ?: ""
        val localLlmClient = if (localLlmUrl.isNotBlank()) HttpLocalRehabLlmClient(localLlmUrl) else null

        coordinator = RehabAssistantCoordinator(sessionRepository, localLlmClient)
    }

    private fun initViews() {
        btnBackChat = findViewById(R.id.btnBackChat)
        btnClearChat = findViewById(R.id.btnClearChat)
        rvChatMessages = findViewById(R.id.rvChatMessages)
        layoutChatEmptyState = findViewById(R.id.layoutChatEmptyState)
        etChatInput = findViewById(R.id.etChatInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)

        chipWhyRep7 = findViewById(R.id.chipWhyRep7)
        chipRejectedYesterday = findViewById(R.id.chipRejectedYesterday)
        chipValidThisWeek = findViewById(R.id.chipValidThisWeek)
        chipSummaryMonth = findViewById(R.id.chipSummaryMonth)
        chipWhichExercise = findViewById(R.id.chipWhichExercise)
        chipExplainToday = findViewById(R.id.chipExplainToday)
        chipLastSession = findViewById(R.id.chipLastSession)

        btnBackChat.setOnClickListener { finish() }

        btnClearChat.setOnClickListener {
            lifecycleScope.launch {
                sessionRepository.clearChatMessages()
                Toast.makeText(this@ChatActivity, "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
        }

        adapter = ChatAdapter { card ->
            showEvidenceDetailDialog(card)
        }

        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatMessages.layoutManager = layoutManager
        rvChatMessages.adapter = adapter

        btnSendMessage.setOnClickListener {
            val query = etChatInput.text.toString().trim()
            if (query.isNotEmpty()) {
                etChatInput.setText("")
                sendQuery(query)
            }
        }

        // Quick suggestion chips
        chipWhyRep7.setOnClickListener { sendQuery("Why wasn't my 7th rep counted yesterday?") }
        chipRejectedYesterday.setOnClickListener { sendQuery("Show me my rejected reps from yesterday") }
        chipValidThisWeek.setOnClickListener { sendQuery("How many valid repetitions did I complete this week?") }
        chipSummaryMonth.setOnClickListener { sendQuery("Give me a summary of this month") }
        chipWhichExercise.setOnClickListener { sendQuery("Which exercise did I perform most?") }
        chipExplainToday.setOnClickListener { sendQuery("Explain today's session simply") }
        chipLastSession.setOnClickListener { sendQuery("What happened during my last session?") }
    }

    private fun observeChatHistory() {
        lifecycleScope.launch {
            sessionRepository.getAllChatMessages().collectLatest { messages ->
                if (messages.isEmpty()) {
                    layoutChatEmptyState.visibility = View.VISIBLE
                    rvChatMessages.visibility = View.GONE
                } else {
                    layoutChatEmptyState.visibility = View.GONE
                    rvChatMessages.visibility = View.VISIBLE
                    adapter.updateMessages(messages)
                    rvChatMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendQuery(query: String) {
        btnSendMessage.isEnabled = false

        lifecycleScope.launch {
            // 1. Record User Message in Room
            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "USER",
                content = query,
                timestamp = System.currentTimeMillis()
            )
            sessionRepository.insertChatMessage(userMsg)

            // 2. Query Coordinator (retrieval + deterministic / local LLM explanation)
            val (responseText, evidenceCards) = withContext(Dispatchers.IO) {
                coordinator.processQuery(query)
            }

            // 3. Record Assistant Message in Room
            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                sender = "ASSISTANT",
                content = responseText,
                timestamp = System.currentTimeMillis(),
                evidenceCardsJson = StructuredEvidenceCard.listToJson(evidenceCards)
            )
            sessionRepository.insertChatMessage(assistantMsg)

            btnSendMessage.isEnabled = true
        }
    }

    private fun showEvidenceDetailDialog(card: StructuredEvidenceCard) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_session_rep_inspection)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvSummary: TextView = dialog.findViewById(R.id.tvDialogSessionSummary)
        val rvReps: RecyclerView = dialog.findViewById(R.id.rvDialogReps)
        val tvNoReps: TextView = dialog.findViewById(R.id.tvNoRepsRecorded)
        val btnClose: Button = dialog.findViewById(R.id.btnDialogClose)

        tvSummary.text = "Rep #${card.repNumber} · ${card.exerciseName}"

        lifecycleScope.launch {
            val reps = withContext(Dispatchers.IO) {
                sessionRepository.getRepsForSessionSync(card.sessionId)
            }

            if (reps.isEmpty()) {
                // Show single synthetic rep for inspection if session reps aren't available
                val repEntity = RepEntity(
                    sessionId = card.sessionId,
                    repNumber = card.repNumber,
                    isValid = card.isValid,
                    startTimestampMs = card.timestamp,
                    peakTimestampMs = card.timestamp,
                    endTimestampMs = card.timestamp,
                    durationMs = 2500L,
                    peakKneeAngle = card.measuredAngle,
                    startKneeAngle = 175.0,
                    endKneeAngle = 175.0,
                    feedbackMessage = card.rejectionReason,
                    evidenceImagePath = card.evidenceImagePath,
                    targetAngle = card.targetAngle
                )
                tvNoReps.visibility = View.GONE
                rvReps.visibility = View.VISIBLE
                rvReps.layoutManager = LinearLayoutManager(this@ChatActivity)
                rvReps.adapter = RepInspectionAdapter(listOf(repEntity))
            } else {
                tvNoReps.visibility = View.GONE
                rvReps.visibility = View.VISIBLE
                rvReps.layoutManager = LinearLayoutManager(this@ChatActivity)
                rvReps.adapter = RepInspectionAdapter(reps)
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
