package com.enjiws.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Layar Playlist: Favorit + playlist buatan pengguna. Ketuk lagu untuk
// memutar (lagu itu jadi antrean, jadi autoplay & lagu terkait tetap jalan).
@Composable
fun PlaylistScreen(onPlayReady: (queue: List<OnlineTrack>, index: Int, url: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var openId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    val favorites = PlaylistStore.favorites
    val playlists = PlaylistStore.playlists

    fun playFrom(queue: List<OnlineTrack>, index: Int) {
        val track = queue[index]
        scope.launch {
            try {
                if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank() && track.pipedId.isBlank()) {
                    status = "Lagu ini tidak bisa diputar."
                    return@launch
                }
                status = "Memuat: ${track.title}..."
                val url = resolveTrackUrl(track)
                onPlayReady(queue, index, url)
                status = "Memutar: ${track.title}"
            } catch (e: Exception) {
                status = "Tidak dapat diputar: ${friendlyError(e)}"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Playlist", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        val detailId = openId
        if (detailId == null) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("Nama playlist baru...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        PlaylistStore.createPlaylist(newName.trim())
                        newName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BUAT PLAYLIST")
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().clickable { openId = "fav" }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("♥", color = Color(0xFFFF5252), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Favorit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${favorites.size} lagu", color = Color(0xFF9AA99D), fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF253129))
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { openId = p.id }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${p.tracks.size} lagu", color = Color(0xFF9AA99D), fontSize = 12.sp)
                        }
                        TextButton(onClick = { PlaylistStore.deletePlaylist(p.id) }) {
                            Text("HAPUS", color = Color(0xFF9AA99D), fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF253129))
                }
            }
        } else {
            val isFav = detailId == "fav"
            val listName = if (isFav) "Favorit"
                else playlists.firstOrNull { it.id == detailId }?.name ?: ""
            val tracks = if (isFav) favorites
                else playlists.firstOrNull { it.id == detailId }?.tracks.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openId = null }) {
                    Text("‹ KEMBALI", color = Color(0xFF8AFF00), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(listName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            if (tracks.isNotEmpty()) {
                Button(onClick = { playFrom(tracks, 0) }, modifier = Modifier.fillMaxWidth()) {
                    Text("PUTAR SEMUA")
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    if (isFav) "Belum ada lagu favorit — ketuk ♥ di hasil pencarian."
                    else "Playlist ini masih kosong.",
                    color = Color(0xFF9AA99D), fontSize = 13.sp
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().clickable { playFrom(tracks, index) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtThumb(track.artworkUrl, 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${track.artist}  ·  ${track.source}", color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1)
                        }
                        TextButton(onClick = {
                            if (isFav) PlaylistStore.toggleFavorite(track)
                            else PlaylistStore.removeFromPlaylist(detailId, track)
                        }) {
                            Text("HAPUS", color = Color(0xFF9AA99D), fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFF253129))
                }
            }
        }
    }
}
