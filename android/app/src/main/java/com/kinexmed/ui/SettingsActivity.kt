package com.kinexmed.ui

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        btnBackSettings.setOnClickListener {
            finish()
        }

        btnSaveServerUrl.setOnClickListener {
            saveServerUrl()
        }

        btnTestConnection.setOnClickListener {
            testServerConnection()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(SyncManager.PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(SyncManager.KEY_SERVER_URL, SyncManager.DEFAULT_SERVER_URL)
        etServerUrl.setText(savedUrl)
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
