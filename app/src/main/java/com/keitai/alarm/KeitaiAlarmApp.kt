package com.keitai.alarm

import android.app.Application
import com.keitai.alarm.scheduler.AlarmScheduler

class KeitaiAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reschedule alarms on application creation
        val scheduler = AlarmScheduler(this)
        scheduler.rescheduleAllAlarms()
    }
}
