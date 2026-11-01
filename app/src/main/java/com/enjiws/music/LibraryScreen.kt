package com.enjiws.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?
)

// Baca semua file musik di HP lewat MediaStore. Butuh izin READ_MEDIA_AUDIO
// (diminta dari MainActivity sebelum layar ini memuat daftar).
suspend fun loadLocalSongs(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
    val list = mutableListOf<LocalSong>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    context.contentResolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val albumId = cursor.getLong(albumCol)
            val artwork = if (albumId > 0) {
                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            } else null
            list.add(
                LocalSong(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = cursor.getString(artistCol) ?: "Unknown artist",
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(uri, id),
                    artworkUri = artwork
                )
            )
        }
    }
    list
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayLocal: (List<LocalSong>, Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            songs = loadLocalSongs(context)
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Lokal", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (!hasPermission) "Izinkan akses musik untuk melihat lagu di HP."
            else if (!loaded) "Memuat lagu..."
            else if (songs.isEmpty()) "Belum ada lagu di HP."
            else "${songs.size} lagu di HP",
            color = Color(0xFF9AA99D), fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        if (!hasPermission) {
            Button(onClick = onRequestPermission) { Text("Izinkan Akses Musik") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPlayLocal(songs, index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (song.artworkUri != null) {
                            AsyncImage(
                                model = song.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF253129)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("♪", color = Color(0xFF9AA99D), fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(
                                "${song.artist}  ·  ${song.durationMs / 60000}:${((song.durationMs / 1000) % 60).toString().padStart(2, '0')}",
                                color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF253129))
                }
            }
        }
    }
}
