package com.kinexmed.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kinexmed.R
import com.kinexmed.data.entity.RepEntity
import org.json.JSONArray
import java.io.File
import java.util.Locale

class RepInspectionAdapter(
    private val reps: List<RepEntity>
) : RecyclerView.Adapter<RepInspectionAdapter.RepViewHolder>() {

    class RepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tvRepInspectionNumber)
        val tvStatus: TextView = itemView.findViewById(R.id.tvRepInspectionStatus)
        val tvPeakAngle: TextView = itemView.findViewById(R.id.tvRepInspectionPeakAngle)
        val tvDuration: TextView = itemView.findViewById(R.id.tvRepInspectionDuration)
        val tvFeedback: TextView = itemView.findViewById(R.id.tvRepInspectionFeedback)
        val tvFailureReasons: TextView = itemView.findViewById(R.id.tvRepInspectionFailureReasons)
        val ivEvidenceThumb: ImageView = itemView.findViewById(R.id.ivRepEvidenceThumbnail)
        val tvVideoSeek: TextView = itemView.findViewById(R.id.tvRepVideoSeekTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rep_inspection, parent, false)
        return RepViewHolder(view)
    }

    override fun getItemCount(): Int = reps.size

    override fun onBindViewHolder(holder: RepViewHolder, position: Int) {
        val rep = reps[position]

        holder.tvNumber.text = "Repetition #${rep.repNumber}"

        if (rep.isValid) {
            holder.tvStatus.text = "VALID"
            holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
            holder.tvStatus.setTextColor(0xFF10B981.toInt())
            holder.tvFailureReasons.visibility = View.GONE
        } else {
            holder.tvStatus.text = "INVALID"
            holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_amber)
            holder.tvStatus.setTextColor(0xFFF59E0B.toInt())

            val reasons = parseReasons(rep.failureReasonsJson)
            if (reasons.isNotEmpty()) {
                holder.tvFailureReasons.visibility = View.VISIBLE
                holder.tvFailureReasons.text = "Issues: " + reasons.joinToString(", ")
            } else {
                holder.tvFailureReasons.visibility = View.GONE
            }
        }

        val targetStr = if (rep.targetAngle > 0.0) String.format(Locale.US, " (Target ≥ %.0f°)", rep.targetAngle) else ""
        holder.tvPeakAngle.text = String.format(Locale.US, "Peak ROM: %.1f°%s", rep.peakKneeAngle, targetStr)
        holder.tvDuration.text = String.format(Locale.US, "Duration: %.1fs", rep.durationMs / 1000.0)
        holder.tvFeedback.text = rep.feedbackMessage.ifBlank { "Standard trajectory measured." }

        // Bind evidence screenshot thumbnail if present
        if (!rep.evidenceImagePath.isNullOrBlank()) {
            val imgFile = File(rep.evidenceImagePath)
            if (imgFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bitmap != null) {
                        holder.ivEvidenceThumb.setImageBitmap(bitmap)
                        holder.ivEvidenceThumb.visibility = View.VISIBLE
                    } else {
                        holder.ivEvidenceThumb.visibility = View.GONE
                    }
                } catch (_: Exception) {
                    holder.ivEvidenceThumb.visibility = View.GONE
                }
            } else {
                holder.ivEvidenceThumb.visibility = View.GONE
            }
        } else {
            holder.ivEvidenceThumb.visibility = View.GONE
        }

        // Bind video timeline tag if video timestamp exists
        if (rep.videoTimestampMs != null && rep.videoTimestampMs > 0L) {
            val sec = (rep.videoTimestampMs / 1000).toInt()
            holder.tvVideoSeek.text = String.format(Locale.ROOT, "Timeline Offset: %02d:%02d in session video", sec / 60, sec % 60)
            holder.tvVideoSeek.visibility = View.VISIBLE
        } else {
            holder.tvVideoSeek.visibility = View.GONE
        }
    }

    private fun parseReasons(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
        return list
    }
}
