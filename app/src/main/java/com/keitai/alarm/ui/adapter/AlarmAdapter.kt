package com.keitai.alarm.ui.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.model.Alarm
import com.keitai.alarm.model.RingtoneType

class AlarmAdapter(
    private val context: Context,
    private var alarms: List<Alarm>,
    private val onToggleClick: (Alarm) -> Unit,
    private val onItemClick: (Alarm) -> Unit,
    private val onItemLongClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    var selectedPosition: Int = 0

    fun updateData(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }

    fun getAlarmAt(position: Int): Alarm? {
        if (position in alarms.indices) return alarms[position]
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.bind(alarm, position)
    }

    override fun getItemCount(): Int = alarms.size

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAlarmTime: TextView = itemView.findViewById(R.id.tvAlarmTime)
        private val tvAlarmStatus: TextView = itemView.findViewById(R.id.tvAlarmStatus)
        private val tvAlarmLabel: TextView = itemView.findViewById(R.id.tvAlarmLabel)
        private val tvAlarmRepeat: TextView = itemView.findViewById(R.id.tvAlarmRepeat)
        private val tvAlarmRingtone: TextView = itemView.findViewById(R.id.tvAlarmRingtone)
        private val tvFullSongBadge: TextView = itemView.findViewById(R.id.tvFullSongBadge)

        fun bind(alarm: Alarm, position: Int) {
            tvAlarmTime.text = alarm.getFormattedTime()
            tvAlarmLabel.text = if (alarm.label.isNotBlank()) alarm.label else "Báo thức"
            tvAlarmRepeat.text = alarm.getRepeatDaysSummary()

            // Status Badge
            if (alarm.isEnabled) {
                tvAlarmStatus.text = "BẬT"
                tvAlarmStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_success))
                tvAlarmTime.alpha = 1.0f
                tvAlarmLabel.alpha = 1.0f
            } else {
                tvAlarmStatus.text = "TẮT"
                tvAlarmStatus.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                tvAlarmTime.alpha = 0.5f
                tvAlarmLabel.alpha = 0.5f
            }

            // Ringtone title
            val ringtoneText = when (alarm.ringtoneType) {
                RingtoneType.RANDOM_PLAYLIST -> "🎵 [Random] ${alarm.playlistName ?: "Mặc định"}"
                RingtoneType.CUSTOM_FILE -> "🎵 ${alarm.ringtoneTitle}"
                RingtoneType.DEFAULT -> "🎵 Chuông mặc định máy"
            }
            tvAlarmRingtone.text = ringtoneText

            // Full song badge
            if (alarm.playFullSong) {
                tvFullSongBadge.visibility = View.VISIBLE
                tvFullSongBadge.text = "[Full bài]"
                tvFullSongBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_primary))
            } else {
                tvFullSongBadge.visibility = View.GONE
            }

            // Click handling
            itemView.setOnClickListener {
                selectedPosition = bindingAdapterPosition
                onToggleClick(alarm)
            }

            itemView.setOnLongClickListener {
                selectedPosition = bindingAdapterPosition
                onItemLongClick(alarm)
                true
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    selectedPosition = bindingAdapterPosition
                }
            }
        }
    }
}
