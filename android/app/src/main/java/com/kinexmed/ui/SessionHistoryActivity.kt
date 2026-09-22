package com.kinexmed.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.entity.SessionEntity
import com.kinexmed.data.repository.SessionRepository
import com.kinexmed.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionHistoryActivity : AppCompatActivity() {

    private lateinit var rvSessions: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var btnSyncNow: Button
    private lateinit var btnBackHistory: ImageButton
    private lateinit var btnStartFromEmpty: Button

    private lateinit var sessionRepository: SessionRepository
    private lateinit var syncManager: SyncManager
    private lateinit var adapter: SessionHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        initViews()
        initData()
    }

    private fun initViews() {
        rvSessions = findViewById(R.id.rvSessions)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        btnBackHistory = findViewById(R.id.btnBackHistory)
        btnStartFromEmpty = findViewById(R.id.btnStartFromEmpty)

        btnBackHistory.setOnClickListener {
            finish()
        }

        btnStartFromEmpty.setOnClickListener {
            startActivity(Intent(this, ExercisePreparationActivity::class.java))
            finish()
        }

        rvSessions.layoutManager = LinearLayoutManager(this)
        adapter = SessionHistoryAdapter { session ->
            showRepInspectionDialog(session)
        }
        rvSessions.adapter = adapter

        btnSyncNow.setOnClickListener {
            triggerManualSync()
        }
    }

    private fun initData() {
        val db = AppDatabase.getInstance(this)
        sessionRepository = SessionRepository(db.sessionDao(), db.repDao(), db.evidenceEventDao())
        syncManager = SyncManager(sessionRepository, this)

        lifecycleScope.launch {
            sessionRepository.allSessions.collectLatest { sessions ->
                if (sessions.isEmpty()) {
                    rvSessions.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    rvSessions.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    adapter.updateData(sessions)
                }
            }
        }
    }

    private fun triggerManualSync() {
        btnSyncNow.isEnabled = false
        btnSyncNow.text = "Syncing..."

        lifecycleScope.launch {
            val syncedCount = withContext(Dispatchers.IO) {
                syncManager.syncPendingSessions(forceAll = true)
            }

            btnSyncNow.isEnabled = true
            btnSyncNow.text = "Sync"

            if (syncedCount > 0) {
                Toast.makeText(
                    this@SessionHistoryActivity,
                    "Successfully synchronized $syncedCount session(s)!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@SessionHistoryActivity,
                    "Sync complete. No offline sessions pending.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showRepInspectionDialog(session: SessionEntity) {
        lifecycleScope.launch {
            val reps = withContext(Dispatchers.IO) {
                sessionRepository.getRepsForSessionSync(session.id)
            }

            val dialog = Dialog(this@SessionHistoryActivity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_session_rep_inspection)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val tvSummary: TextView = dialog.findViewById(R.id.tvDialogSessionSummary)
            val rvReps: RecyclerView = dialog.findViewById(R.id.rvDialogReps)
            val tvNoReps: TextView = dialog.findViewById(R.id.tvNoRepsRecorded)
            val btnClose: Button = dialog.findViewById(R.id.btnDialogClose)

            val exerciseTitle = when (session.exerciseName.lowercase()) {
                "squat" -> "Bilateral Squat"
                else -> session.exerciseName.replaceFirstChar { it.uppercase() }
            }
            tvSummary.text = "$exerciseTitle · ${session.validReps} of ${session.totalReps} Valid Reps"

            if (reps.isEmpty()) {
                tvNoReps.visibility = View.VISIBLE
                rvReps.visibility = View.GONE
            } else {
                tvNoReps.visibility = View.GONE
                rvReps.visibility = View.VISIBLE
                rvReps.layoutManager = LinearLayoutManager(this@SessionHistoryActivity)
                rvReps.adapter = RepInspectionAdapter(reps)
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
