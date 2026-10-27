package com.keitai.alarm.model

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Mặc định",
    var tracks: MutableList<PlaylistItem> = mutableListOf()
) {
    fun getTrackCount(): Int = tracks.size

    fun getRandomTrack(): PlaylistItem? {
        val active = tracks.filter { it.isEnabled }
        return if (active.isNotEmpty()) active.random() else null
    }
}
