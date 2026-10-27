package com.keitai.alarm.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.keitai.alarm.R
import com.keitai.alarm.data.AlarmRepository
import com.keitai.alarm.data.PlaylistRepository
import com.keitai.alarm.model.Alarm
import com.keitai.alarm.model.RingtoneType
import com.keitai.alarm.scheduler.AlarmScheduler
import java.io.File
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    private lateinit var tvEditTitle: TextView
    private lateinit var btnSaveTop: TextView
    private lateinit var boxHour: LinearLayout
    private lateinit var tvHour: TextView
    private lateinit var boxMinute: LinearLayout
    private lateinit var tvMinute: TextView
    private lateinit var tvT9InputHint: TextView
    private lateinit var etLabel: EditText

    private lateinit var btnRepeatOnce: TextView
    private lateinit var btnRepeatDaily: TextView
    private lateinit var btnRepeatWeekdays: TextView

    private val dayChips = mutableMapOf<Int, TextView>()

    private lateinit var optRandomRingtone: LinearLayout
    private lateinit var tvOptRandomTitle: TextView
    private lateinit var optCustomFile: LinearLayout
    private lateinit var tvOptCustomTitle: TextView
    private lateinit var tvSelectedSongName: TextView
    private lateinit var optDefaultRingtone: LinearLayout
    private lateinit var tvOptDefaultTitle: TextView
    private lateinit var btnPreviewRingtone: TextView

    private lateinit var btnToggleFullSong: LinearLayout
    private lateinit var tvFullSongTitle: TextView
    private lateinit var btnToggleVibrate: LinearLayout
    private lateinit var tvVibrateTitle: TextView

    private lateinit var btnSave: TextView
    private lateinit var btnCancel: TextView

    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler

    private var alarmId: Long = -1L
    private var isNewAlarm: Boolean = true

    // Working state
    private var currentHour: Int = 7
    private var currentMinute: Int = 0
    private var selectedDays = mutableSetOf<Int>()
    private var currentRingtoneType: RingtoneType = RingtoneType.RANDOM_PLAYLIST
    private var customRingtoneUri: String? = null
    private var customRingtoneTitle: String = "Chưa chọn bài hát"
    private var playFullSong: Boolean = true
    private var vibrateEnabled: Boolean = true

    // T9 Numeric Typing buffer (e.g. typing 0 7 3 0)
    private var t9DigitBuffer = StringBuilder()
    private var isEditingHourField = true

    private var previewPlayer: MediaPlayer? = null
    private var isPreviewPlaying = false

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        private const val REQUEST_PICK_AUDIO = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)

        repository = AlarmRepository.getInstance(this)
        scheduler = AlarmScheduler(this)

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        isNewAlarm = (alarmId == -1L)

        initViews()
        loadAlarmData()
        updateUI()

        // Request initial focus on time input
        boxHour.requestFocus()
    }

    private fun initViews() {
        tvEditTitle = findViewById(R.id.tvEditTitle)
        btnSaveTop = findViewById(R.id.btnSaveTop)
        boxHour = findViewById(R.id.boxHour)
        tvHour = findViewById(R.id.tvHour)
        boxMinute = findViewById(R.id.boxMinute)
        tvMinute = findViewById(R.id.tvMinute)
        tvT9InputHint = findViewById(R.id.tvT9InputHint)
        etLabel = findViewById(R.id.etLabel)

        btnRepeatOnce = findViewById(R.id.btnRepeatOnce)
        btnRepeatDaily = findViewById(R.id.btnRepeatDaily)
        btnRepeatWeekdays = findViewById(R.id.btnRepeatWeekdays)

        dayChips[Calendar.MONDAY] = findViewById(R.id.chipMon)
        dayChips[Calendar.TUESDAY] = findViewById(R.id.chipTue)
        dayChips[Calendar.WEDNESDAY] = findViewById(R.id.chipWed)
        dayChips[Calendar.THURSDAY] = findViewById(R.id.chipThu)
        dayChips[Calendar.FRIDAY] = findViewById(R.id.chipFri)
        dayChips[Calendar.SATURDAY] = findViewById(R.id.chipSat)
        dayChips[Calendar.SUNDAY] = findViewById(R.id.chipSun)

        optRandomRingtone = findViewById(R.id.optRandomRingtone)
        tvOptRandomTitle = findViewById(R.id.tvOptRandomTitle)
        optCustomFile = findViewById(R.id.optCustomFile)
        tvOptCustomTitle = findViewById(R.id.tvOptCustomTitle)
        tvSelectedSongName = findViewById(R.id.tvSelectedSongName)
        optDefaultRingtone = findViewById(R.id.optDefaultRingtone)
        tvOptDefaultTitle = findViewById(R.id.tvOptDefaultTitle)
        btnPreviewRingtone = findViewById(R.id.btnPreviewRingtone)

        btnToggleFullSong = findViewById(R.id.btnToggleFullSong)
        tvFullSongTitle = findViewById(R.id.tvFullSongTitle)
        btnToggleVibrate = findViewById(R.id.btnToggleVibrate)
        tvVibrateTitle = findViewById(R.id.tvVibrateTitle)

        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // Setup click listeners
        btnSaveTop.setOnClickListener { saveAndExit() }
        btnSave.setOnClickListener { saveAndExit() }
        btnCancel.setOnClickListener { finish() }

        boxHour.setOnClickListener {
            isEditingHourField = true
            boxHour.requestFocus()
        }
        boxMinute.setOnClickListener {
            isEditingHourField = false
            boxMinute.requestFocus()
        }

        // Repeat Presets
        btnRepeatOnce.setOnClickListener {
            selectedDays.clear()
            updateRepeatChips()
        }
        btnRepeatDaily.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
            ))
            updateRepeatChips()
        }
        btnRepeatWeekdays.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            ))
            updateRepeatChips()
        }

        // Individual day clicks
        for ((day, chip) in dayChips) {
            chip.setOnClickListener {
                toggleDay(day)
            }
        }

        // Ringtone Radio options
        optRandomRingtone.setOnClickListener {
            currentRingtoneType = RingtoneType.RANDOM_PLAYLIST
            updateRingtoneUI()
        }
        optCustomFile.setOnClickListener {
            currentRingtoneType = RingtoneType.CUSTOM_FILE
            pickCustomAudioFile()
        }
        optDefaultRingtone.setOnClickListener {
            currentRingtoneType = RingtoneType.DEFAULT
            updateRingtoneUI()
        }

        btnPreviewRingtone.setOnClickListener {
            togglePreviewAudio()
        }

        btnToggleFullSong.setOnClickListener {
            playFullSong = !playFullSong
            updateFullSongUI()
        }

        btnToggleVibrate.setOnClickListener {
            vibrateEnabled = !vibrateEnabled
            updateVibrateUI()
        }
    }

    private fun loadAlarmData() {
        if (!isNewAlarm) {
            val existing = repository.getAlarmById(alarmId)
            if (existing != null) {
                currentHour = existing.hour
                currentMinute = existing.minute
                etLabel.setText(existing.label)
                selectedDays.clear()
                selectedDays.addAll(existing.repeatDays)
                currentRingtoneType = existing.ringtoneType
                customRingtoneUri = existing.ringtoneUri
                customRingtoneTitle = existing.ringtoneTitle
                playFullSong = existing.playFullSong
                vibrateEnabled = existing.vibrate
                tvEditTitle.text = "SỬA BÁO THỨC ${existing.getFormattedTime()}"
            }
        } else {
            // New alarm defaults
            val now = Calendar.getInstance()
            currentHour = (now.get(Calendar.HOUR_OF_DAY) + 1) % 24
            currentMinute = 0
            etLabel.setText("Báo thức")
            selectedDays.clear()
            currentRingtoneType = RingtoneType.RANDOM_PLAYLIST
            playFullSong = true
            vibrateEnabled = true
            tvEditTitle.text = "TẠO BÁO THỨC MỚI"
        }
    }

    private fun updateUI() {
        tvHour.text = String.format("%02d", currentHour)
        tvMinute.text = String.format("%02d", currentMinute)
        updateRepeatChips()
        updateRingtoneUI()
        updateFullSongUI()
        updateVibrateUI()
    }

    private fun updateRepeatChips() {
        for ((day, chip) in dayChips) {
            val isSelected = selectedDays.contains(day)
            if (isSelected) {
                chip.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_primary))
                chip.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            } else {
                chip.setBackgroundResource(R.drawable.bg_button_selector)
                chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
    }

    private fun toggleDay(day: Int) {
        if (selectedDays.contains(day)) {
            selectedDays.remove(day)
        } else {
            selectedDays.add(day)
        }
        updateRepeatChips()
    }

    private fun updateRingtoneUI() {
        when (currentRingtoneType) {
            RingtoneType.RANDOM_PLAYLIST -> {
                tvOptRandomTitle.text = "● [A] Chọn ngẫu nhiên từ Playlist"
                tvOptRandomTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_secondary))
                tvOptCustomTitle.text = "○ [B] Chọn bài hát riêng từ máy / SD Card"
                tvOptCustomTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvOptDefaultTitle.text = "○ [C] Chuông mặc định của máy"
                tvOptDefaultTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
            RingtoneType.CUSTOM_FILE -> {
                tvOptRandomTitle.text = "○ [A] Chọn ngẫu nhiên từ Playlist"
                tvOptRandomTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvOptCustomTitle.text = "● [B] Bài hát: $customRingtoneTitle"
                tvOptCustomTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_secondary))
                tvOptDefaultTitle.text = "○ [C] Chuông mặc định của máy"
                tvOptDefaultTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
            RingtoneType.DEFAULT -> {
                tvOptRandomTitle.text = "○ [A] Chọn ngẫu nhiên từ Playlist"
                tvOptRandomTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvOptCustomTitle.text = "○ [B] Chọn bài hát riêng từ máy / SD Card"
                tvOptCustomTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvOptDefaultTitle.text = "● [C] Chuông mặc định của máy"
                tvOptDefaultTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_secondary))
            }
        }
        tvSelectedSongName.text = customRingtoneTitle
    }

    private fun updateFullSongUI() {
        if (playFullSong) {
            tvFullSongTitle.text = "✔ PHÁT FULL TRỌN BÀI HÁT (BẬT)"
            tvFullSongTitle.setTextColor(ContextCompat.getColor(this, R.color.accent_success))
        } else {
            tvFullSongTitle.text = "○ Giới hạn thời gian (TẮT)"
            tvFullSongTitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun updateVibrateUI() {
        if (vibrateEnabled) {
            tvVibrateTitle.text = "✔ Rung kèm theo khi reo (BẬT)"
            tvVibrateTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        } else {
            tvVibrateTitle.text = "○ Rung kèm theo khi reo (TẮT)"
            tvVibrateTitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun pickCustomAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_AUDIO)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở bộ chọn file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            customRingtoneUri = uri.toString()
            customRingtoneTitle = queryFileName(uri) ?: "Bài hát đã chọn"
            currentRingtoneType = RingtoneType.CUSTOM_FILE
            updateRingtoneUI()
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) return it.getString(nameIndex)
                }
            }
        }
        return uri.path?.let { File(it).name }
    }

    private fun togglePreviewAudio() {
        if (isPreviewPlaying) {
            stopPreview()
        } else {
            startPreview()
        }
    }

    private fun startPreview() {
        stopPreview()

        var soundUri: Uri? = null
        when (currentRingtoneType) {
            RingtoneType.RANDOM_PLAYLIST -> {
                val playlistRepo = PlaylistRepository.getInstance(this)
                val track = playlistRepo.getRandomTrack()
                if (track != null) {
                    soundUri = if (track.pathOrUri.startsWith("content://")) {
                        Uri.parse(track.pathOrUri)
                    } else {
                        Uri.fromFile(File(track.pathOrUri))
                    }
                    Toast.makeText(this, "Nghe thử: ${track.title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Playlist rỗng! Hãy quét nhạc trước.", Toast.LENGTH_LONG).show()
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            }
            RingtoneType.CUSTOM_FILE -> {
                if (!customRingtoneUri.isNullOrEmpty()) {
                    soundUri = if (customRingtoneUri!!.startsWith("content://")) {
                        Uri.parse(customRingtoneUri)
                    } else {
                        Uri.fromFile(File(customRingtoneUri!!))
                    }
                } else {
                    Toast.makeText(this, "Chưa chọn file!", Toast.LENGTH_SHORT).show()
                }
            }
            RingtoneType.DEFAULT -> {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        }

        val finalUri: Uri = soundUri 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmEditActivity, finalUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                start()
            }
            isPreviewPlaying = true
            btnPreviewRingtone.text = "⏹ DỪNG NGHE THỬ"
            btnPreviewRingtone.setTextColor(ContextCompat.getColor(this, R.color.accent_danger))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Lỗi phát âm thanh", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            previewPlayer = null
            isPreviewPlaying = false
            btnPreviewRingtone.text = "▶ NGHE THỬ NHẠC CHUÔNG"
            btnPreviewRingtone.setTextColor(ContextCompat.getColor(this, R.color.accent_primary))
        }
    }

    private fun saveAndExit() {
        stopPreview()

        val label = etLabel.text.toString().trim().ifEmpty { "Báo thức" }
        val id = if (isNewAlarm) System.currentTimeMillis() else alarmId

        val alarm = Alarm(
            id = id,
            hour = currentHour,
            minute = currentMinute,
            label = label,
            isEnabled = true,
            repeatDays = selectedDays.toList(),
            ringtoneType = currentRingtoneType,
            ringtoneUri = customRingtoneUri,
            ringtoneTitle = if (currentRingtoneType == RingtoneType.CUSTOM_FILE) customRingtoneTitle else "Ngẫu nhiên từ Playlist",
            vibrate = vibrateEnabled,
            volume = 100,
            playFullSong = playFullSong,
            timeoutMinutes = 0
        )

        val success = repository.saveAlarm(alarm)
        if (success) {
            scheduler.scheduleAlarm(alarm)
            Toast.makeText(this, "Đã lưu báo thức ${alarm.getFormattedTime()}", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Lỗi: Đã đạt tối đa 50 báo thức!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * T9 Physical Keypad & D-Pad Logic for Fujitsu Arrows F-03L
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Quick Save with [#] key, Left Soft Key (F1 / MENU), or CALL key
        if (keyCode == KeyEvent.KEYCODE_POUND ||
            keyCode == KeyEvent.KEYCODE_F1 ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_CALL
        ) {
            saveAndExit()
            return true
        }

        // Handle T9 numbers 0-9 for direct time input (only when not editing label text)
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (etLabel.hasFocus()) {
                return super.onKeyDown(keyCode, event)
            }
            val digit = keyCode - KeyEvent.KEYCODE_0
            handleT9DigitInput(digit)
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Adjust current focused field up
                if (boxHour.hasFocus()) {
                    currentHour = (currentHour + 1) % 24
                    tvHour.text = String.format("%02d", currentHour)
                    return true
                } else if (boxMinute.hasFocus()) {
                    currentMinute = (currentMinute + 1) % 60
                    tvMinute.text = String.format("%02d", currentMinute)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Adjust current focused field down
                if (boxHour.hasFocus()) {
                    currentHour = if (currentHour > 0) currentHour - 1 else 23
                    tvHour.text = String.format("%02d", currentHour)
                    return true
                } else if (boxMinute.hasFocus()) {
                    currentMinute = if (currentMinute > 0) currentMinute - 1 else 59
                    tvMinute.text = String.format("%02d", currentMinute)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (boxMinute.hasFocus()) {
                    boxHour.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (boxHour.hasFocus()) {
                    boxMinute.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_CLEAR, KeyEvent.KEYCODE_BACK -> {
                // Back / Clear: Discard
                stopPreview()
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Typing 4 digits on T9 keypad automatically sets Hour & Minute!
     * E.g. typing '0', '7', '3', '0' sets 07:30.
     */
    private fun handleT9DigitInput(digit: Int) {
        t9DigitBuffer.append(digit)
        tvT9InputHint.text = "Đang gõ: ${t9DigitBuffer}..."

        if (t9DigitBuffer.length == 4) {
            val h = t9DigitBuffer.substring(0, 2).toIntOrNull() ?: 7
            val m = t9DigitBuffer.substring(2, 4).toIntOrNull() ?: 0

            currentHour = h.coerceIn(0, 23)
            currentMinute = m.coerceIn(0, 59)

            tvHour.text = String.format("%02d", currentHour)
            tvMinute.text = String.format("%02d", currentMinute)
            tvT9InputHint.text = "Đã đặt: ${String.format("%02d:%02d", currentHour, currentMinute)} ✓"

            t9DigitBuffer.clear()
        }
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }
}
