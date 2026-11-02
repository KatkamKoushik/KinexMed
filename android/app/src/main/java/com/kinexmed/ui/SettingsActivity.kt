package com.kinexmed.ui

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.kinexmed.R
import com.kinexmed.data.db.AppDatabase
import com.kinexmed.data.repository.SessionRepository
import com.kinexmed.sync.SyncManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBackSettings: ImageButton
    private lateinit var etServerUrl: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSaveServerUrl: Button
    private lateinit var tvConnectionStatus: TextView

    private lateinit var switchEvidenceCapture: SwitchCompat
    private lateinit var switchContinuousVideo: SwitchCompat
    private lateinit var etLocalLlmUrl: EditText
    private lateinit var btnSaveAssistantSettings: Button

    private lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val db = AppDatabase.getInstance(this)
        val sessionRepository = SessionRepository(db.sessionDao(), db.repDao(), db.evidenceEventDao())
        syncManager = SyncManager(sessionRepository, this)

        initViews()
        loadPreferences()
    }

    private fun initViews() {
        btnBackSettings = findViewById(R.id.btnBackSettings)
        etServerUrl = findViewById(R.id.etServerUrl)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSaveServerUrl = findViewById(R.id.btnSaveServerUrl)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        switchEvidenceCapture = findViewById(R.id.switchEvidenceCapture)
        switchContinuousVideo = findViewById(R.id.switchContinuousVideo)
        etLocalLlmUrl = findViewById(R.id.etLocalLlmUrl)
        btnSaveAssistantSettings = findViewById(R.id.btnSaveAssistantSettings)

        btnBackSettings.setOnClickListener {
            finish()
        }

        btnSaveServerUrl.setOnClickListener {
            saveServerUrl()
        }

        btnTestConnection.setOnClickListener {
            testServerConnection()
        }

        btnSaveAssistantSettings.setOnClickListener {
            saveAssistantSettings()
        }
    }

    private fun loadPreferences() {
        val serverPrefs = getSharedPreferences(SyncManager.PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = serverPrefs.getString(SyncManager.KEY_SERVER_URL, SyncManager.DEFAULT_SERVER_URL)
        etServerUrl.setText(savedUrl)

        val settingsPrefs = getSharedPreferences("kinexmed_settings", Context.MODE_PRIVATE)
        switchEvidenceCapture.isChecked = settingsPrefs.getBoolean("pref_evidence_capture", true)
        switchContinuousVideo.isChecked = settingsPrefs.getBoolean("pref_opt_in_video_recording", false)
        etLocalLlmUrl.setText(settingsPrefs.getString("pref_local_llm_url", ""))
    }

    private fun saveServerUrl() {
        val url = etServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a valid server URL", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences(SyncManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SyncManager.KEY_SERVER_URL, url).apply()

        Toast.makeText(this, "Server URL saved!", Toast.LENGTH_SHORT).show()
        tvConnectionStatus.text = "Saved: $url"
        tvConnectionStatus.setTextColor(0xFF06B6D4.toInt())
    }

    private fun saveAssistantSettings() {
        val prefs = getSharedPreferences("kinexmed_settings", Context.MODE_PRIVATE)
        val capture = switchEvidenceCapture.isChecked
        val video = switchContinuousVideo.isChecked
        val llmUrl = etLocalLlmUrl.text.toString().trim()

        prefs.edit()
            .putBoolean("pref_evidence_capture", capture)
            .putBoolean("pref_opt_in_video_recording", video)
            .putString("pref_local_llm_url", llmUrl)
            .apply()

        Toast.makeText(this, "Assistant & Evidence settings saved!", Toast.LENGTH_SHORT).show()
    }

    private fun testServerConnection() {
        val url = etServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a server URL to test", Toast.LENGTH_SHORT).show()
            return
        }

        btnTestConnection.isEnabled = false
        tvConnectionStatus.text = "Testing connection to $url..."
        tvConnectionStatus.setTextColor(0xFF94A3B8.toInt())

        lifecycleScope.launch {
            val (success, message) = syncManager.testConnection(url)
            btnTestConnection.isEnabled = true

            if (success) {
                tvConnectionStatus.text = "Success: $message"
                tvConnectionStatus.setTextColor(0xFF10B981.toInt())
            } else {
                tvConnectionStatus.text = "Failed: $message"
                tvConnectionStatus.setTextColor(0xFFEF4444.toInt())
            }
        }
    }
}
