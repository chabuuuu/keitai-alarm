package com.keitai.alarm.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.model.PlaylistItem

class PlaylistAdapter(
    private val context: Context,
    private var playlist: List<PlaylistItem>,
    private val onItemClick: (PlaylistItem) -> Unit,
    private val onDeleteClick: (PlaylistItem) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    var selectedPosition: Int = 0

    fun updateData(newPlaylist: List<PlaylistItem>) {
        playlist = newPlaylist
        notifyDataSetChanged()
    }

    fun getTrackAt(position: Int): PlaylistItem? {
        if (position in playlist.indices) return playlist[position]
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val item = playlist[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = playlist.size

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTrackCheck: TextView = itemView.findViewById(R.id.tvTrackCheck)
        private val tvTrackTitle: TextView = itemView.findViewById(R.id.tvTrackTitle)
        private val tvTrackArtist: TextView = itemView.findViewById(R.id.tvTrackArtist)
        private val btnDeleteTrack: TextView = itemView.findViewById(R.id.btnDeleteTrack)

        fun bind(item: PlaylistItem, position: Int) {
            tvTrackTitle.text = item.title
            val artistAndDuration = "${item.artist} • ${item.getFormattedDuration()}"
            tvTrackArtist.text = artistAndDuration

            if (item.isEnabled) {
                tvTrackCheck.text = "✔"
                tvTrackCheck.setTextColor(ContextCompat.getColor(context, R.color.accent_success))
                tvTrackTitle.alpha = 1.0f
            } else {
                tvTrackCheck.text = "○"
                tvTrackCheck.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                tvTrackTitle.alpha = 0.5f
            }

            itemView.setOnClickListener {
                selectedPosition = bindingAdapterPosition
                onItemClick(item)
            }

            btnDeleteTrack.setOnClickListener {
                onDeleteClick(item)
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    selectedPosition = bindingAdapterPosition
                }
            }
        }
    }
}
