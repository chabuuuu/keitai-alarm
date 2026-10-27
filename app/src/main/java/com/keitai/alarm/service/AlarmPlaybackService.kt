package com.keitai.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.keitai.alarm.R
import com.keitai.alarm.data.AlarmRepository
import com.keitai.alarm.data.PlaylistRepository
import com.keitai.alarm.model.Alarm
import com.keitai.alarm.model.RingtoneType
import com.keitai.alarm.ui.AlarmAlertActivity
import java.io.File

class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarm: Alarm? = null

    companion object {
        const val CHANNEL_ID = "keitai_alarm_channel_high"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM = "com.keitai.alarm.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.keitai.alarm.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.keitai.alarm.ACTION_SNOOZE_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        var isRinging = false
            private set
        var currentAlarmId: Long = -1L
            private set
        var currentPlayingTitle: String = ""
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "KeitaiAlarm:AlarmPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM

        when (action) {
            ACTION_STOP_ALARM -> {
                stopAlarm()
                stopSelf()
            }
            ACTION_SNOOZE_ALARM -> {
                snoozeAlarm()
                stopSelf()
            }
            ACTION_START_ALARM -> {
                val alarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
                startAlarm(alarmId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startAlarm(alarmId: Long) {
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes timeout */)

        val repository = AlarmRepository.getInstance(this)
        val alarm = repository.getAlarmById(alarmId) ?: Alarm(id = alarmId)
        currentAlarm = alarm
        currentAlarmId = alarm.id
        isRinging = true

        val notification = createNotification(alarm)
        startForeground(NOTIFICATION_ID, notification)

        playRingtone(alarm)
        startVibration(alarm)
    }

    private fun playRingtone(alarm: Alarm) {
        stopAudio()

        var ringtoneTitle = "Chuông mặc định"
        var soundUri: Uri? = null

        when (alarm.ringtoneType) {
            RingtoneType.RANDOM_PLAYLIST -> {
                val playlistRepo = PlaylistRepository.getInstance(this)
                val randomTrack = playlistRepo.getRandomTrack(alarm.playlistId)
                if (randomTrack != null) {
                    val pName = alarm.playlistName ?: "Playlist"
                    ringtoneTitle = "${randomTrack.title} ($pName)"
                    soundUri = if (randomTrack.pathOrUri.startsWith("content://")) {
                        Uri.parse(randomTrack.pathOrUri)
                    } else {
                        Uri.fromFile(File(randomTrack.pathOrUri))
                    }
                }
            }
            RingtoneType.CUSTOM_FILE -> {
                if (!alarm.ringtoneUri.isNullOrEmpty()) {
                    ringtoneTitle = alarm.ringtoneTitle
                    soundUri = if (alarm.ringtoneUri!!.startsWith("content://")) {
                        Uri.parse(alarm.ringtoneUri)
                    } else {
                        Uri.fromFile(File(alarm.ringtoneUri!!))
                    }
                }
            }
            RingtoneType.DEFAULT -> {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        }

        val finalUri: Uri = soundUri 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        currentPlayingTitle = ringtoneTitle

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmPlaybackService, finalUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                // If playFullSong is set, we loop it once it completes so it keeps ringing until user turns it off
                isLooping = true

                val volumeFloat = (alarm.volume.coerceIn(10, 100)) / 100f
                setVolume(volumeFloat, volumeFloat)

                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("KeitaiAlarm", "Error playing audio, falling back to default ringtone", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmPlaybackService, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun startVibration(alarm: Alarm) {
        if (!alarm.vibrate) return

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 800, 400, 800, 400, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            vibrator = null
        }
    }

    private fun stopAlarm() {
        stopAudio()
        stopVibration()
        isRinging = false
        currentAlarmId = -1L
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun snoozeAlarm() {
        stopAlarm()
        // Snooze 10 minutes
        currentAlarm?.let { alarm ->
            val snoozeAlarm = alarm.copy(
                id = System.currentTimeMillis() + 600000L,
                label = "${alarm.label} (Báo lại)"
            )
            val scheduler = com.keitai.alarm.scheduler.AlarmScheduler(this)
            // Schedule one-off alarm in 10 minutes
            val trigger = System.currentTimeMillis() + 10 * 60 * 1000L
            val intent = Intent(this, com.keitai.alarm.receiver.AlarmReceiver::class.java).apply {
                action = com.keitai.alarm.receiver.AlarmReceiver.ACTION_FIRE_ALARM
                putExtra(EXTRA_ALARM_ID, snoozeAlarm.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                snoozeAlarm.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val showIntent = Intent(this, com.keitai.alarm.ui.MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                this,
                snoozeAlarm.id.toInt(),
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(trigger, showPendingIntent), pendingIntent)
        }
    }

    private fun createNotification(alarm: Alarm): Notification {
        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarm.id.toInt(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_SNOOZE_ALARM
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Báo thức: ${alarm.getFormattedTime()} - ${alarm.label}")
            .setContentText("Đang phát: $currentPlayingTitle")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .addAction(0, "Tắt chuông", stopPendingIntent)
            .addAction(0, "Báo lại 10p", snoozePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chuông Báo Thức Keitai",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh phát âm thanh báo thức âm lượng cao"
                setSound(null, null) // Sound is handled directly via MediaPlayer with USAGE_ALARM
                enableVibration(false) // Handled by service
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
