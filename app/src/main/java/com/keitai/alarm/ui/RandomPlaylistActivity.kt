package com.keitai.alarm.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keitai.alarm.R
import com.keitai.alarm.data.PlaylistRepository
import com.keitai.alarm.model.Playlist
import com.keitai.alarm.ui.adapter.PlaylistListAdapter

class RandomPlaylistActivity : AppCompatActivity() {

    private lateinit var tvPlaylistCount: TextView
    private lateinit var btnCreatePlaylist: TextView
    private lateinit var btnScanAllAudio: TextView
    private lateinit var rvPlaylists: RecyclerView
    private lateinit var layoutEmptyPlaylists: LinearLayout
    private lateinit var btnCreateEmpty: TextView

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var adapter: PlaylistListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_playlist)

        playlistRepository = PlaylistRepository.getInstance(this)

        initViews()
        setupRecyclerView()
        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        loadPlaylists()
    }

    private fun initViews() {
        tvPlaylistCount = findViewById(R.id.tvPlaylistCount)
        btnCreatePlaylist = findViewById(R.id.btnCreatePlaylist)
        btnScanAllAudio = findViewById(R.id.btnScanAllAudio)
        rvPlaylists = findViewById(R.id.rvPlaylists)
        layoutEmptyPlaylists = findViewById(R.id.layoutEmptyPlaylists)
        btnCreateEmpty = findViewById(R.id.btnCreateEmpty)

        btnCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        btnCreateEmpty.setOnClickListener {
            showCreatePlaylistDialog()
        }

        btnScanAllAudio.setOnClickListener {
            scanAllMusicToDefault()
        }
    }

    private fun setupRecyclerView() {
        adapter = PlaylistListAdapter(
            context = this,
            playlists = emptyList(),
            onItemClick = { playlist ->
                openPlaylistTracks(playlist)
            },
            onDeleteClick = { playlist ->
                confirmDeletePlaylist(playlist)
            }
        )

        rvPlaylists.layoutManager = LinearLayoutManager(this)
        rvPlaylists.adapter = adapter
    }

    private fun loadPlaylists() {
        val list = playlistRepository.getAllPlaylists()
        adapter.updateData(list)
        tvPlaylistCount.text = "${list.size} list"

        if (list.isEmpty()) {
            layoutEmptyPlaylists.visibility = View.VISIBLE
            rvPlaylists.visibility = View.GONE
            btnCreateEmpty.requestFocus()
        } else {
            layoutEmptyPlaylists.visibility = View.GONE
            rvPlaylists.visibility = View.VISIBLE
        }
    }

    private fun openPlaylistTracks(playlist: Playlist) {
        val intent = Intent(this, PlaylistTracksActivity::class.java).apply {
            putExtra(PlaylistTracksActivity.EXTRA_PLAYLIST_ID, playlist.id)
            putExtra(PlaylistTracksActivity.EXTRA_PLAYLIST_NAME, playlist.name)
        }
        startActivity(intent)
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Tên Playlist (VD: Nhạc Anime, Nhạc Nhẹ...)"
            setTextColor(ContextCompat.getColor(this@RandomPlaylistActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@RandomPlaylistActivity, R.color.text_muted))
            setBackgroundResource(R.drawable.bg_button_selector)
            setPadding(24, 24, 24, 24)
        }

        AlertDialog.Builder(this, R.style.SonyDarkDialog)
            .setTitle("Tạo Playlist Mới")
            .setView(input)
            .setPositiveButton("Tạo") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val created = playlistRepository.createPlaylist(name)
                    loadPlaylists()
                    Toast.makeText(this, "Đã tạo list: ${created.name}", Toast.LENGTH_SHORT).show()
                    openPlaylistTracks(created)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeletePlaylist(playlist: Playlist) {
        if (playlist.id == "default_playlist") {
            Toast.makeText(this, "Không thể xóa playlist Mặc định", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.SonyDarkDialog)
            .setTitle("Xóa Playlist")
            .setMessage("Xóa toàn bộ list '${playlist.name}' (${playlist.getTrackCount()} bài)?")
            .setPositiveButton("Xóa") { _, _ ->
                playlistRepository.deletePlaylist(playlist.id)
                loadPlaylists()
                Toast.makeText(this, "Đã xóa list", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun scanAllMusicToDefault() {
        Toast.makeText(this, "Đang quét toàn bộ nhạc trên máy và thẻ nhớ...", Toast.LENGTH_SHORT).show()
        Thread {
            val scanned = playlistRepository.scanDeviceAudio()
            val defaultPlaylist = playlistRepository.getAllPlaylists().firstOrNull() ?: playlistRepository.createPlaylist("Mặc định")
            val added = playlistRepository.addTracksToPlaylist(defaultPlaylist.id, scanned)
            runOnUiThread {
                loadPlaylists()
                Toast.makeText(this, "Quét xong! Đã thêm $added bài vào '${defaultPlaylist.name}'", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    /**
     * T9 Hardware Keypad Handling for F-03L
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                showCreatePlaylistDialog()
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                scanAllMusicToDefault()
                return true
            }
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR -> {
                val current = adapter.getPlaylistAt(adapter.selectedPosition)
                if (current != null) {
                    confirmDeletePlaylist(current)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val current = adapter.getPlaylistAt(adapter.selectedPosition)
                if (current != null) {
                    openPlaylistTracks(current)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
