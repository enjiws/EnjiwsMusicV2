package com.enjiws.music.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enjiws.music.data.ArtistData
import com.enjiws.music.data.Browse
import com.enjiws.music.data.BrowseItem
import com.enjiws.music.data.Track
import com.enjiws.music.data.friendlyError
import com.enjiws.music.playback.PlayerHub
import kotlinx.coroutines.CancellationException

/** Artis bulat: avatar, nama, jumlah pelanggan. */
@Composable
fun ArtistCircle(item: BrowseItem, onClick: () -> Unit) {
    Column(Modifier.width(108.dp).pressable(onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(item.thumb.ifBlank { null }, 92.dp, radius = 46.dp)
        Spacer(Modifier.height(6.dp))
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Satu baris hasil jelajah (album / artis / playlist) untuk daftar vertikal. */
@Composable
fun BrowseRow(item: BrowseItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(item.thumb.ifBlank { null }, 56.dp, radius = if (item.kind == "artist") 28.dp else 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
    }
}

/** Halaman album / playlist: memuat lagu dari YouTube Music lalu memakai tampilan koleksi yang sudah ada. */
@Composable
fun AlbumScreen(item: BrowseItem, onBack: () -> Unit) {
    var tracks by remember(item.url) { mutableStateOf<List<Track>?>(null) }
    var error by remember(item.url) { mutableStateOf<String?>(null) }
    var attempt by remember(item.url) { mutableIntStateOf(0) }

    LaunchedEffect(item.url, attempt) {
        error = null
        tracks = null
        try {
            tracks = Browse.album(item.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = friendlyError(e)
        }
    }

    val list = tracks
    val err = error
    when {
        list != null -> CollectionScreen(
            title = item.name,
            subtitle = listOf(item.subtitle.substringBefore(" · "), "${list.size} lagu").filter { it.isNotBlank() }.joinToString(" · "),
            tracks = list,
            onBack = onBack,
            emptyText = "Album ini tidak berisi lagu yang bisa diputar."
        )
        err != null -> Column(Modifier.fillMaxSize()) {
            BackRow(onBack)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gagal memuat album: $err", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { attempt++ }) { Text("Coba lagi") }
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            BackRow(onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

/** Halaman artis: avatar, lagu populer, dan album. */
@Composable
fun ArtistScreen(item: BrowseItem, onBack: () -> Unit, onOpenAlbum: (BrowseItem) -> Unit) {
    var data by remember(item.url) { mutableStateOf<ArtistData?>(null) }
    LaunchedEffect(item.url) {
        data = try {
            Browse.artist(item.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ArtistData(emptyList(), emptyList())
        }
    }
    val d = data

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BackRow(onBack)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(item.thumb.ifBlank { null }, 140.dp, radius = 70.dp)
                Spacer(Modifier.height(12.dp))
                Text(item.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d != null && d.songs.isNotEmpty()) {
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { PlayerHub.play(d.songs, 0, shuffle = false) }) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Putar")
                        }
                        FilledTonalButton(onClick = { PlayerHub.playShuffled(d.songs) }) {
                            Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Acak")
                        }
                    }
                }
            }
        }
        if (d == null) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            if (d.songs.isNotEmpty()) {
                item { SectionTitle("Lagu populer") }
                itemsIndexed(d.songs.take(15), key = { i, t -> "s$i${t.id}" }) { i, t ->
                    TrackRow(t, showSource = true, onClick = { PlayerHub.play(d.songs, i) })
                }
            }
            if (d.albums.isNotEmpty()) {
                item { SectionTitle("Album") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(d.albums, key = { it.url }) { a ->
                            SquareCard(a.thumb.ifBlank { null }, a.name, a.subtitle, onClick = { onOpenAlbum(a) })
                        }
                    }
                }
            }
            if (d.songs.isEmpty() && d.albums.isEmpty()) {
                item {
                    Text("Tidak ada hasil untuk artis ini.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
