package com.enjiws.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PlayerHub

@Composable
fun PlaylistsScreen(onOpenPlaylist: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlist", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text("Baru")
                }
            }
            Spacer(Modifier.padding(4.dp))
        }
        item {
            ListRow(
                leading = {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                },
                title = "Favorit", subtitle = "${Store.favorites.size} lagu", onClick = { onOpenPlaylist(Store.FAV_ID) }
            )
        }
        items(Store.playlists, key = { it.id }) { p ->
            ListRow(
                leading = { Cover(p.tracks.firstOrNull()?.cover, 52.dp) },
                title = p.name, subtitle = "${p.tracks.size} lagu", onClick = { onOpenPlaylist(p.id) }
            )
        }
        if (Store.playlists.isEmpty()) {
            item {
                Text("Buat playlist untuk mengelompokkan lagu favoritmu. Dari mana pun, pilih ⋮ pada lagu lalu “Tambah ke playlist”.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (creating) NameDialog("Playlist baru", confirm = "Buat", onDismiss = { creating = false }) {
        Store.createPlaylist(it); creating = false
    }
}

@Composable
private fun ListRow(leading: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Halaman detail satu playlist / Favorit, dengan ganti nama & hapus. */
@Composable
fun PlaylistDetailScreen(id: String, onBack: () -> Unit) {
    val isFav = id == Store.FAV_ID
    val playlist = Store.playlist(id)
    LaunchedEffect(playlist, isFav) { if (!isFav && playlist == null) onBack() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val title = if (isFav) "Favorit" else playlist?.name.orEmpty()
    val tracks = Store.tracksOf(id)

    CollectionScreen(
        title = title, subtitle = "${tracks.size} lagu", tracks = tracks, playlistId = id, onBack = onBack,
        emptyText = if (isFav) "Belum ada favorit. Ketuk ikon hati di pemutar atau menu ⋮ pada lagu." else "Playlist ini masih kosong.",
        topActions = {
            if (!isFav) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Hapus playlist") }, onClick = { menu = false; deleting = true })
                }
            }
        }
    )
    if (renaming) NameDialog("Ganti nama", initial = title, onDismiss = { renaming = false }) { Store.renamePlaylist(id, it); renaming = false }
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Hapus “$title”?") },
        text = { Text("Playlist dihapus, lagunya tidak.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { deleting = false; Store.deletePlaylist(id) }) { Text("Hapus") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text("Batal") } }
    )
}

/** Halaman detail generik: cover besar, judul, tombol Putar/Acak, lalu daftar lagu. */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    playlistId: String? = null,
    emptyText: String = "Tidak ada lagu.",
    topActions: @Composable () -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
                Spacer(Modifier.weight(1f))
                topActions()
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(tracks.firstOrNull()?.cover, 120.dp, radius = 14.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { PlayerHub.play(tracks, 0, shuffle = false) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Putar")
                }
                FilledTonalButton(onClick = { PlayerHub.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Acak")
                }
            }
        }
        if (tracks.isEmpty()) {
            item { Text(emptyText, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(tracks, key = { i, t -> "$i${t.id}" }) { i, t ->
            TrackRow(t, playlistId = playlistId, showSource = true, onClick = { PlayerHub.play(tracks, i) })
        }
    }
}
