package com.enjiws.music.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enjiws.music.data.Downloads
import androidx.compose.material3.CircularProgressIndicator
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.DownloadOptions
import com.enjiws.music.data.DownloadOption
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PlayerHub
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- util

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (total % 3600) / 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}

fun formatSeconds(s: Long) = formatTime(s * 1000)

/** Posisi pemutar terkini; hanya "berdetak" selama composable-nya tampil dan lagu diputar. */
@Composable
fun rememberPosition(intervalMs: Long = 500L): State<Long> {
    val playing = PlayerHub.state.collectAsState().value.isPlaying
    return produceState(PlayerHub.position(), playing) {
        while (true) {
            value = PlayerHub.position()
            delay(if (playing) intervalMs else 1000L)
        }
    }
}

// ------------------------------------------------------------- gambar

@Composable
fun Cover(model: Any?, size: Dp, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    CoverBox(model, modifier.size(size), radius, iconSize = size * 0.45f)
}

@Composable
fun CoverBox(model: Any?, modifier: Modifier, radius: Dp = 12.dp, iconSize: Dp = 28.dp) {
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        // Ikon di bawah, gambar di atas: kalau gambar gagal dimuat, ikon tetap terlihat.
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize))
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Empat batang equalizer kecil sebagai penanda lagu sedang diputar. */
@Composable
fun PlayingBars(active: Boolean = true, color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "bars")
    val durations = listOf(550, 750, 650, 850)
    val heights = durations.map { d ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse), label = "b$d").value
    }
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(Modifier.width(3.dp).height((4 + (if (active) h else 0.3f) * 12).dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

// --------------------------------------------------------------- baris

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(AccentGradient))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

/** Satu baris lagu. [playlistId] diteruskan ke menu supaya "hapus dari playlist" muncul di konteks yang tepat. */
@Composable
fun TrackRow(
    track: Track,
    modifier: Modifier = Modifier,
    playlistId: String? = null,
    showSource: Boolean = false,
    onClick: () -> Unit
) {
    val now = PlayerHub.state.collectAsState().value
    val current = now.track?.key == track.key
    val openMenu = LocalTrackMenu.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(track.cover, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            val sub = buildString {
                append(track.artist)
                if (track.durationSeconds > 0) append(" · ").append(formatSeconds(track.durationSeconds))
                if (showSource && track.source.isNotBlank() && !track.isLocal) append(" · ").append(track.source)
                if (track.isPreview) append(" · Pratinjau")
            }
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current) {
            PlayingBars(active = now.isPlaying)
            Spacer(Modifier.width(6.dp))
        }
        IconButton(onClick = { openMenu(track, playlistId) }) {
            Icon(Icons.Rounded.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ----------------------------------------------------------- mini player

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s by PlayerHub.state.collectAsState()
    val track = s.track ?: return
    val pos by rememberPosition(500)
    val progress = if (s.durationMs > 0) (pos.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 2.dp
    ) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.cover, 48.dp, radius = 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { PlayerHub.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Sebelumnya") }
                IconButton(
                    onClick = { PlayerHub.togglePlay() },
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(AccentGradient)
                ) {
                    Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.isPlaying) "Jeda" else "Putar",
                        tint = Color(0xFF0B1500))
                }
                IconButton(onClick = { PlayerHub.next() }) { Icon(Icons.Rounded.SkipNext, "Berikutnya") }
            }
            if (s.buffering) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = Color.Transparent)
        }
    }
}

// ------------------------------------------------------------- menu lagu

/** Buka menu lagu: (track, playlistId bila sedang di dalam playlist). Disediakan oleh [TrackMenuHost]. */
val LocalTrackMenu = staticCompositionLocalOf<(Track, String?) -> Unit> { { _, _ -> } }

private data class MenuTarget(val track: Track, val playlistId: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<MenuTarget?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(LocalTrackMenu provides { t, p -> target = MenuTarget(t, p) }) {
        content()
    }
    val tgt = target ?: return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingPlaylist by remember(tgt) { mutableStateOf(false) }
    var choosingQuality by remember(tgt) { mutableStateOf(false) }
    var newName by remember(tgt) { mutableStateOf("") }
    val track = tgt.track
    val close = { target = null }

    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(track.cover, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (choosingQuality) {
                QualityChooser(track) { opt -> Downloads.start(context, track, opt); close() }
            } else if (!choosingPlaylist) {
                val fav = Store.isFavorite(track)
                val playable = track.canStream
                if (playable) {
                    MenuItem(Icons.Rounded.PlayArrow, "Putar berikutnya") { PlayerHub.playNext(track); close() }
                    MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Tambahkan ke antrean") { PlayerHub.addToQueue(track); close() }
                }
                MenuItem(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Hapus dari Favorit" else "Tambah ke Favorit") {
                    Store.toggleFavorite(track); close()
                }
                MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Tambah ke playlist…") { choosingPlaylist = true }
                if (!track.isLocal && playable && !track.isPreview) {
                    MenuItem(Icons.Rounded.Download, "Unduh") {
                        if (Prefs.askDownloadQuality) choosingQuality = true else { Downloads.start(context, track); close() }
                    }
                }
                MenuItem(Icons.Rounded.Share, "Bagikan") {
                    val link = track.youtubeUrl.ifBlank { track.soundcloudUrl.ifBlank { track.storeUrl } }
                    val text = if (link.isNotBlank()) "${track.title} – ${track.artist}\n$link" else "${track.title} – ${track.artist}"
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                    )
                    close()
                }
                tgt.playlistId?.let { pid ->
                    if (pid != Store.FAV_ID) {
                        MenuItem(Icons.Rounded.ArrowUpward, "Pindah ke atas") { Store.moveInPlaylist(pid, track, -1); close() }
                        MenuItem(Icons.Rounded.ArrowDownward, "Pindah ke bawah") { Store.moveInPlaylist(pid, track, 1); close() }
                    }
                    MenuItem(Icons.Rounded.Delete, "Hapus dari daftar ini") { Store.removeFrom(pid, track); close() }
                }
            } else {
                Text("Tambah ke playlist", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
                LazyColumn(Modifier.height(if (Store.playlists.size > 3) 220.dp else (Store.playlists.size * 56 + 8).dp.coerceAtLeast(8.dp))) {
                    items(Store.playlists, key = { it.id }) { p ->
                        MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "${p.name}  (${p.tracks.size})") {
                            Store.addToPlaylist(p.id, track)
                            com.enjiws.music.data.UiBus.toast("Ditambahkan ke “${p.name}”")
                            close()
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it }, singleLine = true,
                        placeholder = { Text("Playlist baru…") }, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        if (newName.isNotBlank()) {
                            val p = Store.createPlaylist(newName)
                            Store.addToPlaylist(p.id, track)
                            com.enjiws.music.data.UiBus.toast("Ditambahkan ke “${p.name}”")
                            close()
                        }
                    }) { Icon(Icons.Rounded.Add, "Buat") }
                }
            }
        }
    }
}

/** Daftar kualitas unduhan yang benar-benar tersedia untuk lagu ini. */
@Composable
private fun QualityChooser(track: Track, onPick: (DownloadOption) -> Unit) {
    val options by produceState<List<DownloadOption>?>(null, track.id) {
        value = runCatching { DownloadOptions.forTrack(track) }.getOrElse { emptyList() }
    }
    Text("Kualitas unduhan", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
    val list = options
    when {
        list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> Text("Tidak ada pilihan kualitas untuk lagu ini.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Column {
            list.forEach { o ->
                Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(o.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(o.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (track.subId.isBlank()) {
                Text(
                    "Audio dari YouTube/SoundCloud selalu lossy. Lossless dan Hi-Res hanya tersedia untuk lagu dari servermu.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dialog satu kolom teks (nama playlist dll.). */
@Composable
fun NameDialog(title: String, initial: String = "", confirm: String = "Simpan", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Kartu persegi (cover + judul + subjudul) untuk baris horizontal di Beranda. */
@Composable
fun SquareCard(cover: Any?, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(140.dp).pressable(onClick).padding(4.dp)) {
        CoverBox(cover, Modifier.size(132.dp).shadow(12.dp, RoundedCornerShape(18.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), radius = 18.dp, iconSize = 44.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
