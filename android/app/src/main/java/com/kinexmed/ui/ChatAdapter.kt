package com.kinexmed.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.kinexmed.R
import com.kinexmed.data.entity.ChatMessageEntity
import com.kinexmed.domain.memory.StructuredEvidenceCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val onEvidenceCardClicked: (StructuredEvidenceCard) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessageEntity>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val cardTimeFormat = SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault())

    fun updateMessages(newMessages: List<ChatMessageEntity>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutUser: LinearLayout = itemView.findViewById(R.id.layoutUserMessage)
        private val tvUserText: TextView = itemView.findViewById(R.id.tvUserMessageText)
        private val tvUserTime: TextView = itemView.findViewById(R.id.tvUserTimestamp)

        private val layoutAssistant: LinearLayout = itemView.findViewById(R.id.layoutAssistantMessage)
        private val tvAssistantText: TextView = itemView.findViewById(R.id.tvAssistantMessageText)
        private val tvAssistantTime: TextView = itemView.findViewById(R.id.tvAssistantTimestamp)
        private val llEvidenceCards: LinearLayout = itemView.findViewById(R.id.llEvidenceCardsContainer)

        fun bind(message: ChatMessageEntity) {
            val formattedTime = timeFormat.format(Date(message.timestamp))

            if (message.sender == "USER") {
                layoutUser.visibility = View.VISIBLE
                layoutAssistant.visibility = View.GONE
                tvUserText.text = message.content
                tvUserTime.text = formattedTime
            } else {
                layoutUser.visibility = View.GONE
                layoutAssistant.visibility = View.VISIBLE
                tvAssistantText.text = message.content
                tvAssistantTime.text = formattedTime

                llEvidenceCards.removeAllViews()
                val cards = StructuredEvidenceCard.listFromJson(message.evidenceCardsJson)

                if (cards.isNotEmpty()) {
                    llEvidenceCards.visibility = View.VISIBLE
                    val inflater = LayoutInflater.from(itemView.context)

                    for (card in cards) {
                        val cardView = inflater.inflate(R.layout.item_evidence_card, llEvidenceCards, false)
                        val cardRoot: CardView = cardView.findViewById(R.id.cardEvidenceRoot)
                        val tvTitle: TextView = cardView.findViewById(R.id.tvCardRepTitle)
                        val tvBadge: TextView = cardView.findViewById(R.id.tvCardStatusBadge)
                        val tvMeasured: TextView = cardView.findViewById(R.id.tvCardMeasuredAngle)
                        val tvTarget: TextView = cardView.findViewById(R.id.tvCardTargetAngle)
                        val tvReason: TextView = cardView.findViewById(R.id.tvCardReason)
                        val tvTimestamp: TextView = cardView.findViewById(R.id.tvCardTimestamp)
                        val ivThumb: ImageView = cardView.findViewById(R.id.ivEvidenceThumbnail)
                        val tvSeek: TextView = cardView.findViewById(R.id.tvCardVideoSeekTag)

                        tvTitle.text = "Rep #${card.repNumber} · ${card.exerciseName}"
                        tvMeasured.text = "${card.measuredAngle.toInt()}${card.unit}"
                        tvTarget.text = "≥ ${card.targetAngle.toInt()}${card.unit}"
                        tvReason.text = card.rejectionReason.ifEmpty { "Criteria satisfied" }
                        tvTimestamp.text = cardTimeFormat.format(Date(card.timestamp))

                        if (card.isValid) {
                            tvBadge.text = "VALID"
                            tvBadge.setTextColor(Color.parseColor("#10B981"))
                            tvBadge.setBackgroundResource(R.drawable.bg_badge_green)
                            tvMeasured.setTextColor(Color.parseColor("#10B981"))
                        } else {
                            tvBadge.text = "REJECTED"
                            tvBadge.setTextColor(Color.parseColor("#EF4444"))
                            tvBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                            tvMeasured.setTextColor(Color.parseColor("#F59E0B"))
                        }

                        // Load evidence frame screenshot thumbnail if present
                        if (!card.evidenceImagePath.isNullOrBlank()) {
                            val imgFile = File(card.evidenceImagePath)
                            if (imgFile.exists()) {
                                try {
                                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bitmap != null) {
                                        ivThumb.setImageBitmap(bitmap)
                                        ivThumb.visibility = View.VISIBLE
                                    } else {
                                        ivThumb.visibility = View.GONE
                                    }
                                } catch (_: Exception) {
                                    ivThumb.visibility = View.GONE
                                }
                            } else {
                                ivThumb.visibility = View.GONE
                            }
                        } else {
                            ivThumb.visibility = View.GONE
                        }

                        if (card.videoTimestampMs != null && card.videoTimestampMs > 0L) {
                            val sec = (card.videoTimestampMs / 1000).toInt()
                            tvSeek.text = String.format(Locale.ROOT, "%02d:%02d in session video ➔", sec / 60, sec % 60)
                        } else {
                            tvSeek.text = "Tap to Inspect Rep ➔"
                        }

                        cardRoot.setOnClickListener {
                            onEvidenceCardClicked(card)
                        }

                        llEvidenceCards.addView(cardView)
                    }
                } else {
                    llEvidenceCards.visibility = View.GONE
                }
            }
        }
    }
}
