package com.keitai.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.keitai.alarm.scheduler.AlarmScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val scheduler = AlarmScheduler(context)
            scheduler.rescheduleAllAlarms()
        }
    }
}
