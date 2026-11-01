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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Layar Antrean: lagu yang sedang diputar, lagu berikutnya dari antrean
// online, dan rekomendasi lagu online berikutnya (dari fitur radio).
// Ketuk baris mana pun untuk langsung memutarnya. Rekomendasi yang diketuk
// ditambahkan ke antrean lalu diputar, jadi radio tetap berlanjut sesudahnya.
@Composable
fun QueueScreen(
    queue: List<OnlineTrack>,
    currentIndex: Int,
    onBack: () -> Unit,
    onPlayReady: (queue: List<OnlineTrack>, index: Int, url: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var related by remember { mutableStateOf<List<OnlineTrack>?>(null) }
    val current = if (currentIndex >= 0) queue.getOrNull(currentIndex) else null
    val upcoming = if (currentIndex >= 0 && currentIndex + 1 < queue.size)
        queue.subList(currentIndex + 1, queue.size) else emptyList()

    LaunchedEffect(current) {
        related = null
        if (current == null) {
            related = emptyList()
        } else {
            val rel = try {
                fetchRelatedTracks(current)
            } catch (e: Exception) {
                emptyList()
            }
            related = rel.filter { r -> queue.none { isSameSong(it, r) } }
        }
    }

    fun playFrom(q: List<OnlineTrack>, index: Int) {
        val track = q.getOrNull(index) ?: return
        scope.launch {
            try {
                if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank() && track.pipedId.isBlank()) {
                    status = "Lagu ini tidak bisa diputar."
                    return@launch
                }
                status = "Memuat: ${track.title}..."
                val url = resolveTrackUrl(track)
                onPlayReady(q, index, url)
                status = "Memutar: ${track.title}"
            } catch (e: Exception) {
                status = "Tidak dapat diputar: ${friendlyError(e)}"
            }
        }
    }

    fun playRecommendation(rec: OnlineTrack) {
        val existing = queue.indexOfFirst { isSameSong(it, rec) }
        if (existing >= 0) {
            playFrom(queue, existing)
        } else {
            val newQueue = queue + rec
            playFrom(newQueue, newQueue.size - 1)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassGlyphButton(onClick = onBack, glyph = "‹", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Text("Antrean", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        if (status.isNotBlank()) {
            Text(status, color = Color(0xFF8AFF00), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (current == null) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Putar lagu online dulu untuk melihat antrean & rekomendasi.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text("SEDANG DIPUTAR", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                QueueRow(track = current, highlighted = true) { playFrom(queue, currentIndex) }
                Spacer(Modifier.height(16.dp))
            }
            if (upcoming.isNotEmpty()) {
                item {
                    Text("BERIKUTNYA", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(upcoming) { j, track ->
                    QueueRow(track = track, highlighted = false) {
                        playFrom(queue, currentIndex + 1 + j)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            item {
                Text("REKOMENDASI", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
            when (val rel = related) {
                null -> item {
                    Text("Memuat rekomendasi...", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                }
                emptyList<OnlineTrack>() -> item {
                    Text(
                        "Tidak ada rekomendasi untuk lagu ini.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }
                else -> items(rel) { rec ->
                    QueueRow(track = rec, highlighted = false) { playRecommendation(rec) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(track: OnlineTrack, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtThumb(url = track.artworkUrl, sizeDp = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (highlighted) Color(0xFF8AFF00) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                track.artist + if (track.durationSeconds > 0)
                    " · ${track.durationSeconds / 60}:${(track.durationSeconds % 60).toString().padStart(2, '0')}" else "",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}
