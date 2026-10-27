package com.keitai.alarm.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keitai.alarm.model.Alarm
import com.keitai.alarm.model.RingtoneType
import java.util.Calendar

class AlarmRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val MAX_ALARMS = 50
        private const val PREFS_NAME = "keitai_alarms_prefs"
        private const val KEY_ALARMS_JSON = "alarms_list_json"

        @Volatile
        private var instance: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return instance ?: synchronized(this) {
                instance ?: AlarmRepository(context).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getAllAlarms(): List<Alarm> {
        val json = prefs.getString(KEY_ALARMS_JSON, null) ?: return getInitialAlarms()
        return try {
            val type = object : TypeToken<List<Alarm>>() {}.type
            val list: List<Alarm> = gson.fromJson(json, type) ?: emptyList()
            list.sortedWith(compareBy({ it.hour }, { it.minute }))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getInitialAlarms(): List<Alarm> {
        // Initial sample alarms for convenient first run
        val initial = listOf(
            Alarm(
                id = 1001L,
                hour = 7,
                minute = 0,
                label = "Thức dậy buổi sáng",
                isEnabled = true,
                repeatDays = listOf(
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY
                ),
                ringtoneType = RingtoneType.RANDOM_PLAYLIST,
                ringtoneTitle = "Ngẫu nhiên từ Playlist",
                playFullSong = true
            ),
            Alarm(
                id = 1002L,
                hour = 8,
                minute = 30,
                label = "Đi làm / Đi học",
                isEnabled = false,
                repeatDays = listOf(
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY
                ),
                ringtoneType = RingtoneType.RANDOM_PLAYLIST,
                ringtoneTitle = "Ngẫu nhiên từ Playlist",
                playFullSong = true
            )
        )
        saveAlarmsList(initial)
        return initial
    }

    @Synchronized
    fun getAlarmById(id: Long): Alarm? {
        return getAllAlarms().firstOrNull { it.id == id }
    }

    @Synchronized
    fun canAddAlarm(): Boolean {
        return getAllAlarms().size < MAX_ALARMS
    }

    @Synchronized
    fun getAlarmCount(): Int {
        return getAllAlarms().size
    }

    @Synchronized
    fun saveAlarm(alarm: Alarm): Boolean {
        val list = getAllAlarms().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == alarm.id }

        if (existingIndex >= 0) {
            // Update existing
            list[existingIndex] = alarm
        } else {
            // Check maximum 50 alarms constraint
            if (list.size >= MAX_ALARMS) {
                return false
            }
            list.add(alarm)
        }

        saveAlarmsList(list)
        return true
    }

    @Synchronized
    fun toggleAlarm(id: Long): Alarm? {
        val list = getAllAlarms().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = list[index].copy(isEnabled = !list[index].isEnabled)
            list[index] = updated
            saveAlarmsList(list)
            return updated
        }
        return null
    }

    @Synchronized
    fun deleteAlarm(id: Long): Boolean {
        val list = getAllAlarms().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            saveAlarmsList(list)
        }
        return removed
    }

    private fun saveAlarmsList(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString(KEY_ALARMS_JSON, json).apply()
    }
}
