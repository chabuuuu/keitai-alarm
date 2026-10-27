package com.keitai.alarm.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.model.Playlist

class PlaylistListAdapter(
    private val context: Context,
    private var playlists: List<Playlist>,
    private val onItemClick: (Playlist) -> Unit,
    private val onDeleteClick: (Playlist) -> Unit
) : RecyclerView.Adapter<PlaylistListAdapter.PlaylistViewHolder>() {

    var selectedPosition: Int = 0

    fun updateData(newList: List<Playlist>) {
        playlists = newList
        notifyDataSetChanged()
    }

    fun getPlaylistAt(position: Int): Playlist? {
        if (position in playlists.indices) return playlists[position]
        return null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_card, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val item = playlists[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = playlists.size

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPlaylistName: TextView = itemView.findViewById(R.id.tvPlaylistName)
        private val tvPlaylistTrackCount: TextView = itemView.findViewById(R.id.tvPlaylistTrackCount)
        private val btnDeletePlaylist: TextView = itemView.findViewById(R.id.btnDeletePlaylist)

        fun bind(item: Playlist, position: Int) {
            tvPlaylistName.text = item.name
            val count = item.getTrackCount()
            tvPlaylistTrackCount.text = "$count bài hát"

            // Protect default playlist from deletion button
            if (item.id == "default_playlist") {
                btnDeletePlaylist.visibility = View.GONE
            } else {
                btnDeletePlaylist.visibility = View.VISIBLE
            }

            itemView.setOnClickListener {
                selectedPosition = bindingAdapterPosition
                onItemClick(item)
            }

            btnDeletePlaylist.setOnClickListener {
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
