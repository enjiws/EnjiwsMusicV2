package com.enjiws.music.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalSong(val track: Track, val folder: String, val dateAddedSec: Long)

/** Musik yang ada di perangkat (MediaStore). */
object LocalLibrary {
    var songs by mutableStateOf<List<LocalSong>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var permitted by mutableStateOf(false)
    /** Diisi MainActivity: meminta izin akses audio. */
    var requestPermission: () -> Unit = {}

    fun refresh(context: Context) {
        if (!permitted || loading) return
        val app = context.applicationContext
        loading = true
        AppScope.launch {
            try { songs = withContext(Dispatchers.IO) { query(app) } } finally { loading = false }
        }
    }

    @Suppress("DEPRECATION")
    private fun query(context: Context): List<LocalSong> {
        val out = mutableListOf<LocalSong>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID
        )
        // RELATIVE_PATH baru ada di Android 10; di bawahnya pakai DATA.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            projection += MediaStore.Audio.Media.DATA
        }

        try {
            context.contentResolver.query(
                base, projection.toTypedArray(),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 20000",
                null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iRel = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val albumId = c.getLong(iAlbumId)
                    val rel = if (iRel >= 0) c.getString(iRel).orEmpty() else ""
                    val data = if (iData >= 0) runCatching { c.getString(iData).orEmpty() }.getOrDefault("") else ""
                    val folder = when {
                        data.isNotBlank() -> data.substringBeforeLast("/", "").substringAfterLast("/", "")
                        rel.isNotBlank() -> rel.trimEnd('/').substringAfterLast("/", "")
                        else -> ""
                    }.ifBlank { "Lainnya" }
                    val artist = c.getString(iArtist).orEmpty().let { if (it.isBlank() || it == "<unknown>") "Artis tidak dikenal" else it }
                    val album = c.getString(iAlbum).orEmpty().let { if (it.isBlank() || it == "<unknown>") "" else it }
                    out += LocalSong(
                        Track(
                            title = c.getString(iTitle).orEmpty().ifBlank { "Tanpa judul" },
                            artist = artist, album = album,
                            artworkUrl = if (albumId > 0) ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"), albumId
                            ).toString() else "",
                            durationSeconds = c.getLong(iDur) / 1000,
                            source = SOURCE_LOCAL,
                            audioUrl = ContentUris.withAppendedId(base, id).toString(),
                            localId = id
                        ),
                        folder, c.getLong(iDate)
                    )
                }
            }
        } catch (e: Exception) {
            // Query gagal -> kembalikan yang sempat terbaca.
        }
        return out
    }
}
