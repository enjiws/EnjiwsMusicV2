package com.enjiws.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.LocalLibrary
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PlayerHub

private enum class LibTab(val label: String) { Songs("Lagu"), Artists("Artis"), Albums("Album"), Folders("Folder") }
private enum class LibSort(val label: String) { Title("Judul"), Artist("Artis"), Recent("Terbaru"), Duration("Durasi") }

/** Daftar lagu untuk halaman detail (artis / album / folder / riwayat). */
fun resolveCollection(kind: String, name: String): List<Track> {
    val songs = LocalLibrary.songs
    return when (kind) {
        "artist" -> songs.filter { it.track.artist == name }.map { it.track }.sortedWith(compareBy({ it.album }, { it.title }))
        "album" -> songs.filter { (it.track.album.ifBlank { "Tanpa album" }) == name }.map { it.track }.sortedBy { it.title }
        "folder" -> songs.filter { it.folder == name }.map { it.track }
        "history" -> Store.history.map { it.track }
        else -> emptyList()
    }
}

/** Nomor pendek untuk argumen rute, supaya nama dengan karakter aneh tidak merusak URL navigasi. */
object CollectionRegistry {
    private val items = mutableListOf<Pair<String, String>>()
    fun put(kind: String, name: String): Int {
        val i = items.indexOf(kind to name)
        if (i >= 0) return i
        items += kind to name
        return items.lastIndex
    }
    fun get(id: Int): Pair<String, String>? = items.getOrNull(id)
}

@Composable
fun LibraryScreen(onOpenCollection: (kind: String, name: String) -> Unit) {
    val context = LocalContext.current
    if (!LocalLibrary.permitted) {
        EmptyState(
            Icons.Rounded.LibraryMusic, "Izinkan akses musik",
            "Supaya lagu di perangkatmu bisa tampil dan diputar.",
            Modifier.fillMaxSize().padding(top = 64.dp)
        ) { Button(onClick = { LocalLibrary.requestPermission() }) { Text("Izinkan") } }
        return
    }

    var tab by rememberSaveable { mutableStateOf(LibTab.Songs) }
    var sort by rememberSaveable { mutableStateOf(LibSort.Title) }
    var filter by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }

    val songs = LocalLibrary.songs
    val filtered = remember(songs, filter, sort) {
        val f = filter.trim()
        val base = if (f.isEmpty()) songs else songs.filter {
            it.track.title.contains(f, true) || it.track.artist.contains(f, true) || it.track.album.contains(f, true)
        }
        when (sort) {
            LibSort.Title -> base
            LibSort.Artist -> base.sortedBy { it.track.artist.lowercase() }
            LibSort.Recent -> base.sortedByDescending { it.dateAddedSec }
            LibSort.Duration -> base.sortedByDescending { it.track.durationSeconds }
        }
    }
    val tracks = filtered.map { it.track }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Perangkat", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { LocalLibrary.refresh(context) }) { Icon(Icons.Rounded.Refresh, "Muat ulang") }
            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Urutkan") }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                LibSort.values().forEach { s ->
                    DropdownMenuItem(text = { Text((if (s == sort) "✓ " else "") + s.label) }, onClick = { sort = s; sortMenu = false })
                }
            }
        }
        OutlinedTextField(
            value = filter, onValueChange = { filter = it }, singleLine = true,
            placeholder = { Text("Cari di perangkat") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Rounded.Clear, "Hapus") } },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibTab.values().forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
        }

        when {
            LocalLibrary.loading && songs.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.padding(32.dp)); CircularProgressIndicator()
            }
            songs.isEmpty() -> EmptyState(Icons.Rounded.LibraryMusic, "Tidak ada lagu", "Belum ada file musik ditemukan di perangkat.", Modifier.padding(top = 48.dp))
            tab == LibTab.Songs -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { PlayerHub.play(tracks, 0, shuffle = false) }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Putar semua")
                        }
                        FilledTonalButton(onClick = { PlayerHub.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                            Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Acak")
                        }
                    }
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, onClick = { PlayerHub.play(tracks, i) }) }
            }
            else -> {
                val groups = remember(filtered, tab) {
                    when (tab) {
                        LibTab.Artists -> filtered.groupBy { it.track.artist }
                        LibTab.Albums -> filtered.groupBy { it.track.album.ifBlank { "Tanpa album" } }
                        else -> filtered.groupBy { it.folder }
                    }.entries.sortedBy { it.key.lowercase() }
                }
                val kind = when (tab) { LibTab.Artists -> "artist"; LibTab.Albums -> "album"; else -> "folder" }
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(groups, key = { it.key }) { g ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenCollection(kind, g.key) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(g.value.first().track.cover, 52.dp, radius = if (tab == LibTab.Artists) 26.dp else 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(g.key, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                Text("${g.value.size} lagu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
