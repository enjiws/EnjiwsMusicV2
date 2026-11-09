package com.enjiws.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enjiws.music.data.LocalLibrary
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PlayerHub
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenHistory: () -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 4..10 -> "Selamat pagi"; in 11..14 -> "Selamat siang"; in 15..17 -> "Selamat sore"; else -> "Selamat malam" }
    val history = Store.history
    val favorites = Store.favorites
    val playlists = Store.playlists
    val newest = LocalLibrary.songs.sortedByDescending { it.dateAddedSec }.take(15).map { it.track }
    val frequent = history.filter { it.plays > 1 }.sortedByDescending { it.plays }.take(5)
    val empty = history.isEmpty() && favorites.isEmpty() && playlists.isEmpty() && newest.isEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            val dateLine = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date()) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.headlineLarge)
                    Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Pengaturan") }
            }
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    .clickable(onClick = onOpenSearch),
                shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Cari lagu, artis, atau album", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (history.isNotEmpty()) {
                val hero = history.first().track
                HeroCard(hero) { PlayerHub.play(history.take(15).map { it.track }, 0) }
            }
            MoodCard()
        }

        if (empty) {
            item {
                EmptyState(
                    Icons.Rounded.LibraryMusic, "Belum ada musik di sini",
                    "Cari lagu dari internet, atau izinkan akses ke musik di perangkatmu lewat tab Perangkat.",
                    Modifier.padding(top = 32.dp)
                ) { Button(onClick = onOpenSearch) { Text("Cari musik") } }
            }
            return@LazyColumn
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("Terakhir diputar") { TextButton(onClick = onOpenHistory) { Text("Semua", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val list = history.take(15).map { it.track }
                    itemsIndexed(list, key = { i, t -> "h$i${t.id}" }) { i, t ->
                        SquareCard(t.cover, t.title, t.artist, onClick = { PlayerHub.play(list, i) })
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionTitle("Favorit") { TextButton(onClick = { onOpenPlaylist(Store.FAV_ID) }) { Text("Semua", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val list = favorites.take(20)
                    itemsIndexed(list, key = { i, t -> "f$i${t.id}" }) { i, t ->
                        SquareCard(t.cover, t.title, t.artist, onClick = { PlayerHub.play(list, i) })
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item { SectionTitle("Playlist kamu") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        SquareCard(p.tracks.firstOrNull()?.cover, p.name, "${p.tracks.size} lagu", onClick = { onOpenPlaylist(p.id) })
                    }
                }
            }
        }

        if (frequent.isNotEmpty()) {
            item { SectionTitle("Paling sering diputar") }
            val list = frequent.map { it.track }
            itemsIndexed(list, key = { i, t -> "q$i${t.id}" }) { i, t ->
                TrackRow(t, showSource = true, onClick = { PlayerHub.play(list, i) })
            }
        }

        if (newest.isNotEmpty()) {
            item { SectionTitle("Baru ditambahkan di perangkat") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(newest, key = { i, t -> "n$i${t.id}" }) { i, t ->
                        SquareCard(t.cover, t.title, t.artist, onClick = { PlayerHub.play(newest, i) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Kartu hero editorial: lagu terakhir yang diputar, dengan glow emerald. */
@Composable
private fun HeroCard(track: Track, onPlay: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(Modifier.background(Brush.verticalGradient(listOf(Emerald.copy(alpha = 0.20f), Color.Transparent)))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.cover, 104.dp, radius = 16.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("LANJUTKAN MENDENGARKAN", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(AccentGradient)
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Putar", tint = Color(0xFF0B1500), modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
