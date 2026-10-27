package com.keitai.alarm.data

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keitai.alarm.model.PlaylistItem
import java.io.File
import kotlin.random.Random

class PlaylistRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "keitai_playlist_prefs"
        private const val KEY_PLAYLIST_JSON = "random_playlist_json"

        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getPlaylist(): List<PlaylistItem> {
        val json = prefs.getString(KEY_PLAYLIST_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PlaylistItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addTrack(item: PlaylistItem) {
        val list = getPlaylist().toMutableList()
        if (list.none { it.pathOrUri == item.pathOrUri }) {
            list.add(item)
            savePlaylist(list)
        }
    }

    @Synchronized
    fun addTracks(items: List<PlaylistItem>): Int {
        val list = getPlaylist().toMutableList()
        var addedCount = 0
        for (item in items) {
            if (list.none { it.pathOrUri == item.pathOrUri }) {
                list.add(item)
                addedCount++
            }
        }
        if (addedCount > 0) {
            savePlaylist(list)
        }
        return addedCount
    }

    @Synchronized
    fun removeTrack(id: String) {
        val list = getPlaylist().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            savePlaylist(list)
        }
    }

    @Synchronized
    fun toggleTrack(id: String): Boolean {
        val list = getPlaylist().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = list[index]
            val updated = current.copy(isEnabled = !current.isEnabled)
            list[index] = updated
            savePlaylist(list)
            return updated.isEnabled
        }
        return false
    }

    @Synchronized
    fun getRandomTrack(): PlaylistItem? {
        val active = getPlaylist().filter { it.isEnabled }
        if (active.isEmpty()) return null
        val randomIndex = Random.nextInt(active.size)
        return active[randomIndex]
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

        // 2. Scan standard directories directly (useful on F-03L SD cards and custom music folders)
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

        // Remove duplicates based on pathOrUri
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

    private fun savePlaylist(list: List<PlaylistItem>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_PLAYLIST_JSON, json).apply()
    }
}
