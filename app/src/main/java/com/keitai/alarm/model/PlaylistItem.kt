package com.keitai.alarm.model

data class PlaylistItem(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val artist: String = "Không rõ",
    val pathOrUri: String,
    val durationMs: Long = 0,
    var isEnabled: Boolean = true
) {
    fun getFormattedDuration(): String {
        if (durationMs <= 0) return "--:--"
        val totalSecs = durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
