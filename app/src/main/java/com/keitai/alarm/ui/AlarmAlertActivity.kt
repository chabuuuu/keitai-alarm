package com.keitai.alarm.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.keitai.alarm.R
import com.keitai.alarm.data.AlarmRepository
import com.keitai.alarm.scheduler.AlarmScheduler
import com.keitai.alarm.service.AlarmPlaybackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmAlertActivity : AppCompatActivity() {

    private lateinit var tvAlertClock: TextView
    private lateinit var tvAlertDate: TextView
    private lateinit var tvAlertLabel: TextView
    private lateinit var tvAlertSongTitle: TextView
    private lateinit var btnDismiss: Button
    private lateinit var btnSnooze: Button

    private var alarmId: Long = -1L
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        turnScreenOnAndUnlock()
        setContentView(R.layout.activity_alarm_alert)

        alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)

        initViews()
        loadAlarmInfo()
        updateClock()
        clockHandler.post(clockRunnable)
    }

    private fun turnScreenOnAndUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun initViews() {
        tvAlertClock = findViewById(R.id.tvAlertClock)
        tvAlertDate = findViewById(R.id.tvAlertDate)
        tvAlertLabel = findViewById(R.id.tvAlertLabel)
        tvAlertSongTitle = findViewById(R.id.tvAlertSongTitle)
        btnDismiss = findViewById(R.id.btnDismiss)
        btnSnooze = findViewById(R.id.btnSnooze)

        btnDismiss.setOnClickListener {
            dismissAlarm()
        }

        btnSnooze.setOnClickListener {
            snoozeAlarm()
        }

        btnDismiss.requestFocus()
    }

    private fun loadAlarmInfo() {
        val repo = AlarmRepository.getInstance(this)
        val alarm = repo.getAlarmById(alarmId)

        if (alarm != null) {
            tvAlertLabel.text = if (alarm.label.isNotBlank()) alarm.label else "BÁO THỨC"
        } else {
            tvAlertLabel.text = "BÁO THỨC"
        }

        val playingTitle = AlarmPlaybackService.currentPlayingTitle
        if (playingTitle.isNotEmpty()) {
            tvAlertSongTitle.text = "🎵 $playingTitle"
        } else {
            tvAlertSongTitle.text = "🎵 Chuông báo thức"
        }
    }

    private fun updateClock() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
        val now = Date()
        tvAlertClock.text = timeFormat.format(now)
        tvAlertDate.text = dateFormat.format(now)
    }

    private fun dismissAlarm() {
        val stopIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }

    private fun snoozeAlarm() {
        val snoozeIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
        }
        startService(snoozeIntent)
        finish()
    }

    /**
     * Physical Keypad on F-03L during Alarm Ringing:
     * - Center OK or Key [5] -> Dismiss!
     * - Key [0] or Key [*] -> Snooze!
     * - Volume Up/Down -> Dismiss or Snooze
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                dismissAlarm()
                return true
            }
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_STAR -> {
                snoozeAlarm()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                snoozeAlarm()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }
}
