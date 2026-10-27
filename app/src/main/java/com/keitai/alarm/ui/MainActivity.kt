package com.keitai.alarm.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.data.AlarmRepository
import com.keitai.alarm.data.PlaylistRepository
import com.keitai.alarm.model.Alarm
import com.keitai.alarm.scheduler.AlarmScheduler
import com.keitai.alarm.ui.adapter.AlarmAdapter
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvAlarmCount: TextView
    private lateinit var tvNextAlarm: TextView
    private lateinit var tabAlarms: TextView
    private lateinit var tabRandomPlaylist: TextView
    private lateinit var rvAlarms: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnAddFirst: TextView
    private lateinit var tvSoftkeyGuide: TextView

    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var adapter: AlarmAdapter

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = AlarmRepository.getInstance(this)
        scheduler = AlarmScheduler(this)

        initViews()
        setupRecyclerView()
        checkPermissionsAndBattery()
        loadAlarms()
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
    }

    private fun initViews() {
        tvAlarmCount = findViewById(R.id.tvAlarmCount)
        tvNextAlarm = findViewById(R.id.tvNextAlarm)
        tabAlarms = findViewById(R.id.tabAlarms)
        tabRandomPlaylist = findViewById(R.id.tabRandomPlaylist)
        rvAlarms = findViewById(R.id.rvAlarms)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnAddFirst = findViewById(R.id.btnAddFirst)
        tvSoftkeyGuide = findViewById(R.id.tvSoftkeyGuide)

        tabAlarms.setOnClickListener {
            // Already on alarms
            rvAlarms.requestFocus()
        }

        tabRandomPlaylist.setOnClickListener {
            openRandomPlaylist()
        }

        btnAddFirst.setOnClickListener {
            addNewAlarm()
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            context = this,
            alarms = emptyList(),
            onToggleClick = { alarm ->
                val updated = repository.toggleAlarm(alarm.id)
                if (updated != null) {
                    scheduler.scheduleAlarm(updated)
                    loadAlarms()
                    val statusText = if (updated.isEnabled) "Đã bật báo thức ${updated.getFormattedTime()}" else "Đã tắt báo thức"
                    Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show()
                }
            },
            onItemClick = { alarm ->
                // Center click toggles
                val updated = repository.toggleAlarm(alarm.id)
                if (updated != null) {
                    scheduler.scheduleAlarm(updated)
                    loadAlarms()
                }
            },
            onItemLongClick = { alarm ->
                openEditAlarm(alarm)
            }
        )

        rvAlarms.layoutManager = LinearLayoutManager(this)
        rvAlarms.adapter = adapter
    }

    private fun loadAlarms() {
        val alarms = repository.getAllAlarms()
        adapter.updateData(alarms)

        val count = alarms.size
        tvAlarmCount.text = "Báo thức: $count/50"

        if (alarms.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvAlarms.visibility = View.GONE
            btnAddFirst.requestFocus()
        } else {
            layoutEmpty.visibility = View.GONE
            rvAlarms.visibility = View.VISIBLE
        }

        updateNextAlarmDisplay()
    }

    private fun updateNextAlarmDisplay() {
        val nextInfo = scheduler.getNextUpcomingAlarm()
        if (nextInfo == null) {
            tvNextAlarm.text = "🔔 Chưa bật báo thức nào"
            return
        }

        val (alarm, triggerMillis) = nextInfo
        val diffMillis = triggerMillis - System.currentTimeMillis()
        if (diffMillis <= 0) {
            tvNextAlarm.text = "🔔 Báo thức: ${alarm.getFormattedTime()} (${alarm.label})"
            return
        }

        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
        val remainingText = if (hours > 0) {
            "còn ${hours}h ${minutes}p"
        } else {
            "còn ${minutes} phút"
        }

        tvNextAlarm.text = "🔔 Tiếp theo: ${alarm.getFormattedTime()} - $remainingText"
    }

    private fun addNewAlarm() {
        if (!repository.canAddAlarm()) {
            Toast.makeText(this, "Đã đạt giới hạn tối đa 50 báo thức!", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, AlarmEditActivity::class.java)
        startActivity(intent)
    }

    private fun openEditAlarm(alarm: Alarm) {
        val intent = Intent(this, AlarmEditActivity::class.java).apply {
            putExtra(AlarmEditActivity.EXTRA_ALARM_ID, alarm.id)
        }
        startActivity(intent)
    }

    private fun deleteCurrentAlarm() {
        val currentAlarm = adapter.getAlarmAt(adapter.selectedPosition) ?: return
        AlertDialog.Builder(this, R.style.SonyDarkDialog)
            .setTitle("Xóa báo thức")
            .setMessage("Bạn có chắc chắn muốn xóa báo thức ${currentAlarm.getFormattedTime()} (${currentAlarm.label})?")
            .setPositiveButton("Xóa") { _, _ ->
                scheduler.cancelAlarm(currentAlarm)
                repository.deleteAlarm(currentAlarm.id)
                loadAlarms()
                Toast.makeText(this, "Đã xóa báo thức", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openRandomPlaylist() {
        val intent = Intent(this, RandomPlaylistActivity::class.java)
        startActivity(intent)
    }

    private fun scanMusicLibrary() {
        Toast.makeText(this, "Đang quét nhạc trên máy và thẻ nhớ...", Toast.LENGTH_SHORT).show()
        Thread {
            val playlistRepo = PlaylistRepository.getInstance(this)
            val scanned = playlistRepo.scanDeviceAudio()
            val added = playlistRepo.addTracks(scanned)
            runOnUiThread {
                Toast.makeText(this, "Đã tìm thấy ${scanned.size} bài (Thêm mới: $added bài)", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /**
     * Physical Keypad & D-Pad Handling for Fujitsu Arrows F-03L
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                // Key [1]: Add new alarm
                addNewAlarm()
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                // Key [2]: Edit selected alarm
                val current = adapter.getAlarmAt(adapter.selectedPosition)
                if (current != null) {
                    openEditAlarm(current)
                } else {
                    addNewAlarm()
                }
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                // Key [3]: Open Random Playlist
                openRandomPlaylist()
                return true
            }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_STAR -> {
                // Key [4] or [*]: Delete selected alarm
                deleteCurrentAlarm()
                return true
            }
            KeyEvent.KEYCODE_5 -> {
                // Key [5]: Quick test alarm trigger or scan
                scanMusicLibrary()
                return true
            }
            KeyEvent.KEYCODE_F1 -> {
                // Left Soft Key on F-03L
                addNewAlarm()
                return true
            }
            KeyEvent.KEYCODE_F2 -> {
                // Right Soft Key on F-03L
                openRandomPlaylist()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // Center OK button: toggle currently focused alarm
                val current = adapter.getAlarmAt(adapter.selectedPosition)
                if (current != null) {
                    val updated = repository.toggleAlarm(current.id)
                    if (updated != null) {
                        scheduler.scheduleAlarm(updated)
                        loadAlarms()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkPermissionsAndBattery() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        // Request Ignore Battery Optimization for Fujitsu Arrows F-03L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
