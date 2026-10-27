package com.keitai.alarm.ui

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.data.PlaylistRepository
import com.keitai.alarm.model.PlaylistItem
import com.keitai.alarm.ui.adapter.PlaylistAdapter
import java.io.File

class RandomPlaylistActivity : AppCompatActivity() {

    private lateinit var tvTrackCount: TextView
    private lateinit var btnAddAudioFile: TextView
    private lateinit var btnScanStorage: TextView
    private lateinit var btnTestRandom: TextView
    private lateinit var rvPlaylist: RecyclerView
    private lateinit var layoutEmptyPlaylist: LinearLayout
    private lateinit var btnScanEmpty: TextView

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var adapter: PlaylistAdapter

    private var mediaPlayer: MediaPlayer? = null
    private var playingTrackId: String? = null

    companion object {
        private const val REQUEST_PICK_AUDIO_FILE = 3001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_playlist)

        playlistRepository = PlaylistRepository.getInstance(this)

        initViews()
        setupRecyclerView()
        loadPlaylist()
    }

    private fun initViews() {
        tvTrackCount = findViewById(R.id.tvTrackCount)
        btnAddAudioFile = findViewById(R.id.btnAddAudioFile)
        btnScanStorage = findViewById(R.id.btnScanStorage)
        btnTestRandom = findViewById(R.id.btnTestRandom)
        rvPlaylist = findViewById(R.id.rvPlaylist)
        layoutEmptyPlaylist = findViewById(R.id.layoutEmptyPlaylist)
        btnScanEmpty = findViewById(R.id.btnScanEmpty)

        btnAddAudioFile.setOnClickListener {
            pickAudioFile()
        }

        btnScanStorage.setOnClickListener {
            scanMusic()
        }

        btnTestRandom.setOnClickListener {
            testRandomTrack()
        }

        btnScanEmpty.setOnClickListener {
            scanMusic()
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistAdapter(
            context = this,
            playlist = emptyList(),
            onItemClick = { item ->
                // Toggle play/stop preview or toggle active
                playTrack(item)
            },
            onDeleteClick = { item ->
                deleteTrack(item)
            }
        )

        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.adapter = adapter
    }

    private fun loadPlaylist() {
        val tracks = playlistRepository.getPlaylist()
        adapter.updateData(tracks)
        tvTrackCount.text = "${tracks.size} bài"

        if (tracks.isEmpty()) {
            layoutEmptyPlaylist.visibility = View.VISIBLE
            rvPlaylist.visibility = View.GONE
            btnScanEmpty.requestFocus()
        } else {
            layoutEmptyPlaylist.visibility = View.GONE
            rvPlaylist.visibility = View.VISIBLE
        }
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_AUDIO_FILE)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở bộ chọn file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_AUDIO_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val name = queryFileName(uri) ?: "Bài hát"
            val item = PlaylistItem(
                title = name,
                artist = "Thêm thủ công",
                pathOrUri = uri.toString()
            )
            playlistRepository.addTrack(item)
            loadPlaylist()
            Toast.makeText(this, "Đã thêm: $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return it.getString(idx)
                }
            }
        }
        return uri.path?.let { File(it).name }
    }

    private fun scanMusic() {
        Toast.makeText(this, "Đang quét các thư mục nhạc trên máy...", Toast.LENGTH_SHORT).show()
        Thread {
            val scanned = playlistRepository.scanDeviceAudio()
            val added = playlistRepository.addTracks(scanned)
            runOnUiThread {
                loadPlaylist()
                Toast.makeText(this, "Quét xong! Tìm thấy ${scanned.size} bài, thêm mới $added bài", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun testRandomTrack() {
        val track = playlistRepository.getRandomTrack()
        if (track == null) {
            Toast.makeText(this, "Danh sách rỗng! Hãy thêm bài hát trước.", Toast.LENGTH_SHORT).show()
            return
        }
        playTrack(track)
        Toast.makeText(this, "🎲 Random trúng: ${track.title}", Toast.LENGTH_LONG).show()
    }

    private fun playTrack(item: PlaylistItem) {
        if (playingTrackId == item.id) {
            stopAudio()
            return
        }

        stopAudio()
        try {
            val uri = if (item.pathOrUri.startsWith("content://")) {
                Uri.parse(item.pathOrUri)
            } else {
                Uri.fromFile(File(item.pathOrUri))
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RandomPlaylistActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
                start()
            }
            playingTrackId = item.id
            Toast.makeText(this, "Đang phát: ${item.title}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Không thể phát bài hát này", Toast.LENGTH_SHORT).show()
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
            playingTrackId = null
        }
    }

    private fun deleteTrack(item: PlaylistItem) {
        AlertDialog.Builder(this, R.style.SonyDarkDialog)
            .setTitle("Xóa khỏi Playlist")
            .setMessage("Xóa bài '${item.title}' khỏi danh sách nhạc ngẫu nhiên?")
            .setPositiveButton("Xóa") { _, _ ->
                if (playingTrackId == item.id) stopAudio()
                playlistRepository.removeTrack(item.id)
                loadPlaylist()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /**
     * T9 Hardware Keypad Handling for F-03L
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                pickAudioFile()
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                scanMusic()
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                testRandomTrack()
                return true
            }
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR -> {
                val current = adapter.getTrackAt(adapter.selectedPosition)
                if (current != null) {
                    deleteTrack(current)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val current = adapter.getTrackAt(adapter.selectedPosition)
                if (current != null) {
                    playTrack(current)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
