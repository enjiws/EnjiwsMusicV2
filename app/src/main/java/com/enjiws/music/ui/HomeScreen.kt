package com.enjiws.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enjiws.music.data.BrowseItem
import com.enjiws.music.data.HomeFeed
import com.enjiws.music.data.LocalLibrary
import com.enjiws.music.data.Mix
import com.enjiws.music.data.Prefs
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
    onOpenHistory: () -> Unit,
    onOpenArtist: (BrowseItem) -> Unit,
    onOpenAlbum: (BrowseItem) -> Unit,
    onOpenMix: (Mix) -> Unit
) {
    // Ambil rekomendasi online; dipanggil ulang kalau pilihan bagian Beranda berubah.
    LaunchedEffect(Prefs.hiddenSections) { HomeFeed.refresh() }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 4..10 -> "Selamat pagi"; in 11..14 -> "Selamat siang"; in 15..17 -> "Selamat sore"; else -> "Selamat malam" }
    val history = Store.history
    val favorites = Store.favorites
    val playlists = Store.playlists
    val newest = LocalLibrary.songs.sortedByDescending { it.dateAddedSec }.take(15).map { it.track }
    val frequent = history.filter { it.plays > 1 }.sortedByDescending { it.plays }.take(5)
    val empty = history.isEmpty() && favorites.isEmpty() && playlists.isEmpty() && newest.isEmpty()
    val hasOnline = HomeFeed.popular.isNotEmpty() || HomeFeed.releases.isNotEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val dateLine = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date()) }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ENJIWS · HI-FI", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(greeting, style = MaterialTheme.typography.headlineLarge)
                    Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GlassIconButton(Icons.Rounded.Settings, "Pengaturan", onOpenSettings)
            }

            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).pressable(onOpenSearch), radius = 28.dp) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Cari lagu, artis, album, atau genre", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!empty) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(Icons.Rounded.Favorite, "Favorit", "${favorites.size} lagu", Modifier.weight(1f)) { onOpenPlaylist(Store.FAV_ID) }
                    QuickTile(Icons.Rounded.History, "Riwayat", "${history.size} lagu", Modifier.weight(1f)) { onOpenHistory() }
                }
            }

            if (history.isNotEmpty() && Prefs.sectionOn("continue")) {
                val hero = history.first().track
                HeroCard(hero) { PlayerHub.play(history.take(15).map { it.track }, 0) }
            }
            MoodCard()
            if (HomeFeed.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (empty && !hasOnline && !HomeFeed.loading) {
            item {
                EmptyState(
                    Icons.Rounded.LibraryMusic, "Belum ada musik di sini",
                    "Cari lagu dari internet, atau izinkan akses ke musik di perangkatmu lewat tab Perangkat.",
                    Modifier.padding(top = 32.dp)
                ) { Button(onClick = onOpenSearch) { Text("Cari musik") } }
            }
        }

        if (Prefs.sectionOn("mix") && HomeFeed.mixes.isNotEmpty()) {
            item { SectionTitle("Daily Mix") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(HomeFeed.mixes, key = { it.title }) { m ->
                        SquareCard(m.tracks.first().cover, m.title, m.subtitle, onClick = { onOpenMix(m) })
                    }
                }
            }
        }

        if (Prefs.sectionOn("recent") && history.isNotEmpty()) {
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

        if (Prefs.sectionOn("artists") && HomeFeed.artists.isNotEmpty()) {
            item { SectionTitle("Artis favoritmu") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(HomeFeed.artists, key = { it.url }) { a -> ArtistCircle(a) { onOpenArtist(a) } }
                }
            }
        }

        if (Prefs.sectionOn("albums") && HomeFeed.albums.isNotEmpty()) {
            item { SectionTitle("Album untukmu") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(HomeFeed.albums, key = { it.url }) { a ->
                        SquareCard(a.thumb.ifBlank { null }, a.name, a.subtitle, onClick = { onOpenAlbum(a) })
                    }
                }
            }
        }

        if (Prefs.sectionOn("favorites") && favorites.isNotEmpty()) {
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

        if (Prefs.sectionOn("popular") && HomeFeed.popular.isNotEmpty()) {
            item { SectionTitle("Populer sekarang") }
            item {
                Text("Berdasarkan pencarian YouTube Music", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val list = HomeFeed.popular
                    itemsIndexed(list, key = { i, t -> "p$i${t.id}" }) { i, t ->
                        SquareCard(t.cover, t.title, t.artist, onClick = { PlayerHub.play(list, i) })
                    }
                }
            }
        }

        if (Prefs.sectionOn("releases") && HomeFeed.releases.isNotEmpty()) {
            item { SectionTitle("Rilis baru") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(HomeFeed.releases, key = { it.url }) { a ->
                        SquareCard(a.thumb.ifBlank { null }, a.name, a.subtitle, onClick = { onOpenAlbum(a) })
                    }
                }
            }
        }

        if (Prefs.sectionOn("playlists") && playlists.isNotEmpty()) {
            item { SectionTitle("Playlist kamu") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        SquareCard(p.tracks.firstOrNull()?.cover, p.name, "${p.tracks.size} lagu", onClick = { onOpenPlaylist(p.id) })
                    }
                }
            }
        }

        if (Prefs.sectionOn("frequent") && frequent.isNotEmpty()) {
            item { SectionTitle("Paling sering diputar") }
            val list = frequent.map { it.track }
            itemsIndexed(list, key = { i, t -> "q$i${t.id}" }) { i, t ->
                TrackRow(t, showSource = true, onClick = { PlayerHub.play(list, i) })
            }
        }

        if (Prefs.sectionOn("newest") && newest.isNotEmpty()) {
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

/** Ubin akses cepat bergaya kaca dengan ikon bergradien. */
@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.pressable(onClick), radius = 20.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentGradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Kartu hero: cover lagu terakhir jadi latar penuh, teks di atas gradasi gelap, tombol putar bergradien. */
@Composable
private fun HeroCard(track: Track, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val cover = track.cover
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).height(210.dp)
            .shadow(22.dp, shape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, GlassBorder, shape)
            .pressable(onPlay)
    ) {
        if (cover != null) {
            AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.90f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f).padding(start = 20.dp, bottom = 20.dp)) {
            Text("LANJUTKAN MENDENGARKAN", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(track.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GradientCircleButton(onPlay, 56.dp, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)) {
            Icon(Icons.Rounded.PlayArrow, "Putar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
        }
    }
}
