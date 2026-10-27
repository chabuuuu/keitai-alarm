package com.keitai.alarm.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keitai.alarm.model.Playlist
import com.keitai.alarm.model.PlaylistItem
import java.io.File
import java.util.UUID

class PlaylistRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "keitai_playlist_prefs"
        private const val KEY_PLAYLISTS_LIST_JSON = "named_playlists_json"
        private const val KEY_LEGACY_PLAYLIST_JSON = "random_playlist_json"

        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context).also { instance = it }
            }
        }
    }

    init {
        migrateLegacyPlaylistIfNeeded()
    }

    private fun migrateLegacyPlaylistIfNeeded() {
        if (!prefs.contains(KEY_PLAYLISTS_LIST_JSON) && prefs.contains(KEY_LEGACY_PLAYLIST_JSON)) {
            try {
                val legacyJson = prefs.getString(KEY_LEGACY_PLAYLIST_JSON, null)
                if (!legacyJson.isNullOrEmpty()) {
                    val type = object : TypeToken<List<PlaylistItem>>() {}.type
                    val legacyTracks: List<PlaylistItem> = gson.fromJson(legacyJson, type) ?: emptyList()
                    val defaultPlaylist = Playlist(
                        id = "default_playlist",
                        name = "Mặc định",
                        tracks = legacyTracks.toMutableList()
                    )
                    savePlaylists(listOf(defaultPlaylist))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun getAllPlaylists(): List<Playlist> {
        val json = prefs.getString(KEY_PLAYLISTS_LIST_JSON, null)
        if (json.isNullOrEmpty()) {
            val initial = listOf(
                Playlist(
                    id = "default_playlist",
                    name = "Mặc định",
                    tracks = mutableListOf()
                )
            )
            savePlaylists(initial)
            return initial
        }
        return try {
            val type = object : TypeToken<List<Playlist>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun getPlaylistById(id: String): Playlist? {
        return getAllPlaylists().firstOrNull { it.id == id }
    }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val list = getAllPlaylists().toMutableList()
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Playlist mới" },
            tracks = mutableListOf()
        )
        list.add(newPlaylist)
        savePlaylists(list)
        return newPlaylist
    }

    @Synchronized
    fun renamePlaylist(id: String, newName: String): Boolean {
        val list = getAllPlaylists().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index].name = newName.trim().ifEmpty { "Playlist" }
            savePlaylists(list)
            return true
        }
        return false
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val list = getAllPlaylists().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            // Keep at least one default playlist
            if (list.isEmpty()) {
                list.add(Playlist(id = "default_playlist", name = "Mặc định"))
            }
            savePlaylists(list)
        }
        return removed
    }

    @Synchronized
    fun addTrackToPlaylist(playlistId: String, item: PlaylistItem) {
        val list = getAllPlaylists().toMutableList()
        val target = list.firstOrNull { it.id == playlistId } ?: list.firstOrNull() ?: return
        if (target.tracks.none { it.pathOrUri == item.pathOrUri }) {
            target.tracks.add(item)
            savePlaylists(list)
        }
    }

    @Synchronized
    fun addTracksToPlaylist(playlistId: String, items: List<PlaylistItem>): Int {
        val list = getAllPlaylists().toMutableList()
        val target = list.firstOrNull { it.id == playlistId } ?: list.firstOrNull() ?: return 0
        var addedCount = 0
        for (item in items) {
            if (target.tracks.none { it.pathOrUri == item.pathOrUri }) {
                target.tracks.add(item)
                addedCount++
            }
        }
        if (addedCount > 0) {
            savePlaylists(list)
        }
        return addedCount
    }

    @Synchronized
    fun addTracks(items: List<PlaylistItem>): Int {
        val targetId = getAllPlaylists().firstOrNull()?.id ?: "default_playlist"
        return addTracksToPlaylist(targetId, items)
    }

    @Synchronized
    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val list = getAllPlaylists().toMutableList()
        val target = list.firstOrNull { it.id == playlistId } ?: return
        val removed = target.tracks.removeAll { it.id == trackId }
        if (removed) {
            savePlaylists(list)
        }
    }

    @Synchronized
    fun toggleTrackInPlaylist(playlistId: String, trackId: String): Boolean {
        val list = getAllPlaylists().toMutableList()
        val target = list.firstOrNull { it.id == playlistId } ?: return false
        val track = target.tracks.firstOrNull { it.id == trackId } ?: return false
        track.isEnabled = !track.isEnabled
        savePlaylists(list)
        return track.isEnabled
    }

    /**
     * Pick a random track from the requested playlist.
     * Falls back to any playlist with tracks if the requested playlist is empty or missing.
     */
    @Synchronized
    fun getRandomTrack(playlistId: String?): PlaylistItem? {
        val allPlaylists = getAllPlaylists()
        if (allPlaylists.isEmpty()) return null

        // 1. Try requested playlist
        if (!playlistId.isNullOrEmpty()) {
            val target = allPlaylists.firstOrNull { it.id == playlistId }
            val track = target?.getRandomTrack()
            if (track != null) return track
        }

        // 2. Fallback to default or any playlist with active tracks
        for (playlist in allPlaylists) {
            val track = playlist.getRandomTrack()
            if (track != null) return track
        }

        return null
    }

    /**
     * Scan device directories and MediaStore for music/audio files
     */
    fun scanDeviceAudio(): List<PlaylistItem> {
        val foundTracks = mutableListOf<PlaylistItem>()

        // 1. Query MediaStore Audio
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 15000"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Không tên"
                    val artist = c.getString(artistCol) ?: "Không rõ"
                    val data = c.getString(dataCol) ?: ""
                    val duration = c.getLong(durCol)

                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val pathOrUri = if (data.isNotEmpty() && File(data).exists()) data else uri

                    foundTracks.add(
                        PlaylistItem(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            pathOrUri = pathOrUri,
                            durationMs = duration,
                            isEnabled = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scan standard directories directly (F-03L storage and microSD)
        val candidateDirs = listOf(
            File("/sdcard/Music"),
            File("/sdcard/Download"),
            File("/sdcard/Ringtones"),
            File("/sdcard/Sound"),
            File("/sdcard/YMusic"),
            File("/storage/0403-0201/Music"),
            File("/storage/0403-0201/Download")
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                scanFolderRecursively(dir, foundTracks)
            }
        }

        return foundTracks.distinctBy { it.pathOrUri }
    }

    private fun scanFolderRecursively(folder: File, result: MutableList<PlaylistItem>) {
        val files = folder.listFiles() ?: return
        val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanFolderRecursively(file, result)
                }
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (audioExtensions.contains(ext)) {
                    val path = file.absolutePath
                    if (result.none { it.pathOrUri == path }) {
                        result.add(
                            PlaylistItem(
                                id = file.hashCode().toString(),
                                title = file.nameWithoutExtension,
                                artist = file.parentFile?.name ?: "Nhạc máy",
                                pathOrUri = path,
                                durationMs = 0,
                                isEnabled = true
                            )
                        )
                    }
                }
            }
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_PLAYLISTS_LIST_JSON, json).apply()
    }
}
