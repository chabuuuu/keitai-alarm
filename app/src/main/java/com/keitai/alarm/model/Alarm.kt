package com.keitai.alarm.model

import java.util.Calendar

enum class RingtoneType {
    DEFAULT,
    CUSTOM_FILE,
    RANDOM_PLAYLIST
}

data class Alarm(
    val id: Long = System.currentTimeMillis(),
    var hour: Int = 7,
    var minute: Int = 0,
    var label: String = "Báo thức",
    var isEnabled: Boolean = true,
    var repeatDays: List<Int> = emptyList(), // Calendar.MONDAY (2), Calendar.TUESDAY (3), etc.
    var ringtoneType: RingtoneType = RingtoneType.RANDOM_PLAYLIST,
    var ringtoneUri: String? = null,
    var ringtoneTitle: String = "Ngẫu nhiên từ Playlist",
    var vibrate: Boolean = true,
    var volume: Int = 100,
    var playFullSong: Boolean = true, // Phát full bài không cắt
    var timeoutMinutes: Int = 0       // 0 = Full bài cho đến khi kết thúc hoặc tắt
) {
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun isRepeating(): Boolean = repeatDays.isNotEmpty()

    fun getRepeatDaysSummary(): String {
        if (repeatDays.isEmpty()) return "Một lần"
        if (repeatDays.size == 7) return "Hàng ngày"
        
        val weekdays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val weekends = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val currentSet = repeatDays.toSet()

        if (currentSet == weekdays) return "Thứ 2 - Thứ 6"
        if (currentSet == weekends) return "Cuối tuần (T7, CN)"

        val dayNames = mapOf(
            Calendar.MONDAY to "T2",
            Calendar.TUESDAY to "T3",
            Calendar.WEDNESDAY to "T4",
            Calendar.THURSDAY to "T5",
            Calendar.FRIDAY to "T6",
            Calendar.SATURDAY to "T7",
            Calendar.SUNDAY to "CN"
        )

        val sortedDays = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )

        return sortedDays.filter { repeatDays.contains(it) }.joinToString(" ") { dayNames[it] ?: "" }
    }
}
