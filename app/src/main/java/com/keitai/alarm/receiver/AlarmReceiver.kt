package com.keitai.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.keitai.alarm.data.AlarmRepository
import com.keitai.alarm.scheduler.AlarmScheduler
import com.keitai.alarm.service.AlarmPlaybackService
import com.keitai.alarm.ui.AlarmAlertActivity

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "com.keitai.alarm.ACTION_FIRE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return

        val repository = AlarmRepository.getInstance(context)
        val alarm = repository.getAlarmById(alarmId)

        // 1. Start Audio & Vibration Foreground Service
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_START_ALARM
            putExtra(AlarmPlaybackService.EXTRA_ALARM_ID, alarmId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Launch Fullscreen Alert Activity
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        context.startActivity(alertIntent)

        // 3. Update Alarm State
        if (alarm != null) {
            val scheduler = AlarmScheduler(context)
            if (alarm.repeatDays.isEmpty()) {
                // One-time alarm: auto-disable after ringing
                alarm.isEnabled = false
                repository.saveAlarm(alarm)
            } else {
                // Repeating alarm: schedule next occurrence
                scheduler.scheduleAlarm(alarm)
            }
        }
    }
}
