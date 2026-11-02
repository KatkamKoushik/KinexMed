package com.kinexmed.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kinexmed.R
import com.kinexmed.data.entity.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionHistoryAdapter(
    private var sessions: List<SessionEntity> = emptyList(),
    private val onSessionClick: (SessionEntity) -> Unit
) : RecyclerView.Adapter<SessionHistoryAdapter.SessionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())

    fun updateData(newSessions: List<SessionEntity>) {
        this.sessions = newSessions
        notifyDataSetChanged()
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvExerciseName: TextView = itemView.findViewById(R.id.tvItemExerciseName)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvItemDateTime)
        val tvSyncStatus: TextView = itemView.findViewById(R.id.tvItemSyncStatus)
        val tvReps: TextView = itemView.findViewById(R.id.tvItemReps)
        val tvDuration: TextView = itemView.findViewById(R.id.tvItemDuration)
        val tvPeakRom: TextView = itemView.findViewById(R.id.tvItemPeakRom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun getItemCount(): Int = sessions.size

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        val title = when (session.exerciseName.lowercase()) {
            "squat" -> "Bilateral Squat"
            else -> session.exerciseName.replaceFirstChar { it.uppercase() }
        }
        holder.tvExerciseName.text = title
        holder.tvDateTime.text = dateFormat.format(Date(session.startedAt))

        if (session.syncStatus == "SYNCED") {
            holder.tvSyncStatus.text = "SYNCED"
            holder.tvSyncStatus.setBackgroundResource(R.drawable.bg_badge_green)
            holder.tvSyncStatus.setTextColor(0xFF10B981.toInt())
        } else {
            holder.tvSyncStatus.text = "OFFLINE"
            holder.tvSyncStatus.setBackgroundResource(R.drawable.bg_badge_amber)
            holder.tvSyncStatus.setTextColor(0xFFF59E0B.toInt())
        }

        holder.tvReps.text = "${session.validReps} / ${session.totalReps} Valid"

        val totalSec = session.durationSeconds.toInt()
        val mins = totalSec / 60
        val secs = totalSec % 60
        holder.tvDuration.text = String.format(Locale.US, "%02d:%02d", mins, secs)

        val depthAngle = if (session.minKneeAngle in 30.0..180.0) session.minKneeAngle else session.avgPeakKneeAngle
        holder.tvPeakRom.text = if (depthAngle > 0.0) String.format(Locale.US, "%.0f°", depthAngle) else "--"

        holder.itemView.setOnClickListener {
            onSessionClick(session)
        }
    }
}
