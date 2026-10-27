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

class PlaylistTracksActivity : AppCompatActivity() {

    private lateinit var tvCurrentPlaylistTitle: TextView
    private lateinit var tvTrackCount: TextView
    private lateinit var btnAddAudioFile: TextView
    private lateinit var btnScanStorage: TextView
    private lateinit var btnTestRandom: TextView
    private lateinit var rvTracks: RecyclerView
    private lateinit var layoutEmptyTracks: LinearLayout
    private lateinit var btnScanEmpty: TextView

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var adapter: PlaylistAdapter

    private var playlistId: String = "default_playlist"
    private var playlistName: String = "Mặc định"

    private var mediaPlayer: MediaPlayer? = null
    private var playingTrackId: String? = null

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
        private const val REQUEST_PICK_AUDIO_FILE = 4001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_tracks)

        playlistRepository = PlaylistRepository.getInstance(this)

        playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID) ?: "default_playlist"
        playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Mặc định"

        initViews()
        setupRecyclerView()
        loadPlaylistTracks()
    }

    private fun initViews() {
        tvCurrentPlaylistTitle = findViewById(R.id.tvCurrentPlaylistTitle)
        tvTrackCount = findViewById(R.id.tvTrackCount)
        btnAddAudioFile = findViewById(R.id.btnAddAudioFile)
        btnScanStorage = findViewById(R.id.btnScanStorage)
        btnTestRandom = findViewById(R.id.btnTestRandom)
        rvTracks = findViewById(R.id.rvTracks)
        layoutEmptyTracks = findViewById(R.id.layoutEmptyTracks)
        btnScanEmpty = findViewById(R.id.btnScanEmpty)

        tvCurrentPlaylistTitle.text = "LIST: ${playlistName.uppercase()}"

        btnAddAudioFile.setOnClickListener {
            pickAudioFile()
        }

        btnScanStorage.setOnClickListener {
            scanMusicIntoPlaylist()
        }

        btnTestRandom.setOnClickListener {
            testRandomTrack()
        }

        btnScanEmpty.setOnClickListener {
            scanMusicIntoPlaylist()
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistAdapter(
            context = this,
            playlist = emptyList(),
            onItemClick = { item ->
                playTrack(item)
            },
            onDeleteClick = { item ->
                deleteTrack(item)
            }
        )

        rvTracks.layoutManager = LinearLayoutManager(this)
        rvTracks.adapter = adapter
    }

    private fun loadPlaylistTracks() {
        val playlist = playlistRepository.getPlaylistById(playlistId)
        val tracks = playlist?.tracks ?: emptyList()
        adapter.updateData(tracks)
        tvTrackCount.text = "${tracks.size} bài"

        if (tracks.isEmpty()) {
            layoutEmptyTracks.visibility = View.VISIBLE
            rvTracks.visibility = View.GONE
            btnScanEmpty.requestFocus()
        } else {
            layoutEmptyTracks.visibility = View.GONE
            rvTracks.visibility = View.VISIBLE
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
            playlistRepository.addTrackToPlaylist(playlistId, item)
            loadPlaylistTracks()
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

    private fun scanMusicIntoPlaylist() {
        Toast.makeText(this, "Đang quét nhạc vào list '${playlistName}'...", Toast.LENGTH_SHORT).show()
        Thread {
            val scanned = playlistRepository.scanDeviceAudio()
            val added = playlistRepository.addTracksToPlaylist(playlistId, scanned)
            runOnUiThread {
                loadPlaylistTracks()
                Toast.makeText(this, "Đã thêm $added bài mới vào '${playlistName}'!", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun testRandomTrack() {
        val track = playlistRepository.getRandomTrack(playlistId)
        if (track == null) {
            Toast.makeText(this, "List rỗng! Hãy thêm bài hát trước.", Toast.LENGTH_SHORT).show()
            return
        }
        playTrack(track)
        Toast.makeText(this, "🎲 [${playlistName}] Bốc trúng: ${track.title}", Toast.LENGTH_LONG).show()
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
                setDataSource(this@PlaylistTracksActivity, uri)
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
            .setMessage("Xóa bài '${item.title}' khỏi list '${playlistName}'?")
            .setPositiveButton("Xóa") { _, _ ->
                if (playingTrackId == item.id) stopAudio()
                playlistRepository.removeTrackFromPlaylist(playlistId, item.id)
                loadPlaylistTracks()
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
                scanMusicIntoPlaylist()
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
