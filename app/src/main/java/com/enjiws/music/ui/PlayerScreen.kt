package com.enjiws.music.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.enjiws.music.data.Downloads
import com.enjiws.music.data.Lyrics
import com.enjiws.music.data.LyricsRepo
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.data.UiBus
import com.enjiws.music.playback.AudioFx
import com.enjiws.music.playback.PlayerHub
import com.enjiws.music.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PlayerDark = Color(0xFF0A0D09)
private val PlayerFallback = Color(0xFF263D00)

/** Warna dominan cover untuk latar pemutar (dihaluskan dengan animasi). */
@Composable
private fun rememberDominantColor(model: Any?): Color {
    val context = LocalContext.current
    var color by remember(model) { mutableStateOf(PlayerFallback) }
    LaunchedEffect(model) {
        if (model == null) { color = PlayerFallback; return@LaunchedEffect }
        val res = context.imageLoader.execute(ImageRequest.Builder(context).data(model).size(128).allowHardware(false).build())
        val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val sw = withContext(Dispatchers.Default) {
            val p = Palette.from(bmp).maximumColorCount(16).generate()
            p.darkMutedSwatch ?: p.mutedSwatch ?: p.dominantSwatch
        }
        color = sw?.let { lerp(Color(it.rgb), Color.Black, 0.35f) } ?: PlayerFallback
    }
    return animateColorAsState(color, tween(600), label = "bg").value
}

private enum class PlayerSheet { Speed, Sleep, Eq }

@Composable
fun PlayerScreen(onClose: () -> Unit, onQueue: () -> Unit) {
    val s by PlayerHub.state.collectAsState()
    val track = s.track
    if (track == null) { LaunchedEffect(Unit) { onClose() }; return }

    val openMenu = LocalTrackMenu.current
    val context = LocalContext.current
    val bg = rememberDominantColor(track.cover)
    val white = Color.White
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    val pager = rememberPagerState { 2 }
    val sleepLeft by SleepTimer.remainingMs.collectAsState()
    val sleepEnd by SleepTimer.untilEndOfTrack.collectAsState()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, PlayerDark)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            // Header (tarik ke bawah untuk menutup)
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > 140f) onClose() },
                        onVerticalDrag = { _, d -> total += d }
                    )
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Tutup", tint = white, modifier = Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SEDANG DIPUTAR", color = white.copy(0.7f), fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(
                        if (track.isPreview) "Pratinjau 30 detik" else track.source.ifBlank { "—" },
                        color = white, fontSize = 13.sp, maxLines = 1
                    )
                }
                IconButton(onClick = { openMenu(track, null) }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = white) }
            }

            // Halaman: cover | lirik
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                if (page == 0) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val scale by animateFloatAsState(if (s.isPlaying) 1f else 0.9f, tween(400), label = "scale")
                        val side = minOf(maxWidth, maxHeight)
                        CoverBox(
                            track.cover, Modifier.size(side).graphicsLayer { scaleX = scale; scaleY = scale },
                            radius = 20.dp, iconSize = side * 0.3f
                        )
                    }
                } else {
                    LyricsView(track)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                repeat(2) { i ->
                    Box(Modifier.padding(3.dp).size(if (pager.currentPage == i) 8.dp else 6.dp).clip(CircleShape)
                        .background(white.copy(if (pager.currentPage == i) 0.95f else 0.35f)))
                }
            }

            // Judul + favorit
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = white.copy(0.75f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val fav = Store.isFavorite(track)
                IconButton(onClick = { Store.toggleFavorite(track) }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorit",
                        tint = if (fav) Color(0xFFFF5C7A) else white, modifier = Modifier.size(28.dp))
                }
            }

            SeekBar(durationMs = s.durationMs)

            // Kontrol utama
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { PlayerHub.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Acak", tint = if (s.shuffle) MaterialTheme.colorScheme.primary else white.copy(0.7f))
                }
                IconButton(onClick = { PlayerHub.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", tint = white, modifier = Modifier.size(38.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(white).clickable { PlayerHub.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    if (s.buffering) CircularProgressIndicator(Modifier.size(34.dp), color = PlayerDark, strokeWidth = 3.dp)
                    else Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.isPlaying) "Jeda" else "Putar",
                        tint = PlayerDark, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { PlayerHub.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Berikutnya", tint = white, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { PlayerHub.cycleRepeat() }) {
                    Icon(
                        if (s.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Ulangi",
                        tint = if (s.repeat == Player.REPEAT_MODE_OFF) white.copy(0.7f) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Baris aksi
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Antrean", tint = white) }
                TextButton(onClick = { sheet = PlayerSheet.Speed }) {
                    Text(if (s.speed == 1f) "1x" else "${s.speed}x", color = if (s.speed == 1f) white else MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { sheet = PlayerSheet.Sleep }) {
                    Icon(Icons.Rounded.Timer, "Sleep timer", tint = if (sleepLeft != null || sleepEnd) MaterialTheme.colorScheme.primary else white)
                }
                IconButton(onClick = { sheet = PlayerSheet.Eq }) {
                    Icon(Icons.Rounded.Equalizer, "Equalizer", tint = if (Prefs.eqEnabled) MaterialTheme.colorScheme.primary else white)
                }
                if (!track.isLocal && !track.isPreview) {
                    IconButton(onClick = { Downloads.start(context, track) }) { Icon(Icons.Rounded.Download, "Unduh", tint = white) }
                }
            }
        }
    }

    when (sheet) {
        PlayerSheet.Speed -> SpeedSheet(s.speed) { sheet = null }
        PlayerSheet.Sleep -> SleepSheet { sheet = null }
        PlayerSheet.Eq -> EqualizerSheet { sheet = null }
        null -> Unit
    }
}

@Composable
private fun SeekBar(durationMs: Long) {
    val pos by rememberPosition(250)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = durationMs.coerceAtLeast(1L)
    val shown = if (dragging) (dragValue * dur).toLong() else pos
    Column {
        Slider(
            value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { PlayerHub.seekTo((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.25f))
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown), color = Color.White.copy(0.75f), fontSize = 12.sp)
            Text(if (durationMs > 0) formatTime(durationMs) else "--:--", color = Color.White.copy(0.75f), fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- lirik

@Composable
private fun LyricsView(track: Track) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track.id) { lyrics = null; lyrics = LyricsRepo.get(track) }
    val l = lyrics
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            l == null -> CircularProgressIndicator(color = Color.White)
            l.synced.isNotEmpty() -> SyncedLyrics(l)
            l.plain.isNotBlank() -> Text(
                l.plain, color = Color.White.copy(0.9f), fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
            )
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(36.dp))
                Text("Lirik belum tersedia", color = Color.White.copy(0.6f))
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics) {
    val pos by rememberPosition(300)
    val lines = lyrics.synced
    val current = lines.indexOfLast { it.timeMs <= pos + 200 }
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0 && !state.isScrollInProgress) {
            state.animateScrollToItem(current, -(state.layoutInfo.viewportSize.height / 3))
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        itemsIndexed(lines) { i, line ->
            val alpha by animateFloatAsState(if (i == current) 1f else if (i < current) 0.35f else 0.55f, tween(300), label = "la")
            Text(
                line.text.ifBlank { "♪" },
                color = Color.White.copy(alpha),
                fontSize = if (i == current) 24.sp else 20.sp,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { PlayerHub.seekTo(line.timeMs) }.padding(vertical = 8.dp, horizontal = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(current: Float, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            Text("Kecepatan putar", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    FilterChip(selected = current == v, onClick = { PlayerHub.setSpeed(v); onDismiss() }, label = { Text("${v}x") })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(onDismiss: () -> Unit) {
    val left by SleepTimer.remainingMs.collectAsState()
    val end by SleepTimer.untilEndOfTrack.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Sleep timer", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            left?.let { Text("Berhenti dalam ${formatTime(it)}", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) }
            if (end) Text("Berhenti di akhir lagu ini", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
            listOf(5, 15, 30, 45, 60).forEach { m ->
                SheetRow("$m menit") { SleepTimer.start(m); UiBus.toast("Musik berhenti dalam $m menit"); onDismiss() }
            }
            SheetRow("Akhir lagu ini") { SleepTimer.startEndOfTrack(); onDismiss() }
            if (left != null || end) SheetRow("Matikan timer") { SleepTimer.cancel(); onDismiss() }
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val ready by AudioFx.ready.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = Prefs.eqEnabled, onCheckedChange = { AudioFx.setEnabled(it) }, enabled = ready)
            }
            if (!ready) {
                Text("Equalizer aktif setelah ada lagu yang diputar.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp))
            } else {
                val levels = remember { mutableStateListOf<Float>().also { l -> repeat(AudioFx.bands) { l.add(AudioFx.level(it).toFloat()) } } }
                val presets = remember { AudioFx.presets() }
                if (presets.isNotEmpty()) {
                    Row(Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, name ->
                            FilterChip(selected = false, onClick = {
                                AudioFx.usePreset(i)
                                levels.indices.forEach { b -> levels[b] = AudioFx.level(b).toFloat() }
                            }, label = { Text(name) })
                        }
                    }
                }
                levels.indices.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(AudioFx.freqLabel(b), Modifier.width(64.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = levels[b],
                            onValueChange = { levels[b] = it; AudioFx.setLevel(b, it.toInt()) },
                            onValueChangeFinished = { AudioFx.commitLevels() },
                            valueRange = AudioFx.minLevel.toFloat()..AudioFx.maxLevel.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("Bass Boost", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Slider(value = Prefs.bassBoostStrength.toFloat(), onValueChange = { AudioFx.setBassBoost(it.toInt()) }, valueRange = 0f..1000f)
                Text("Virtualizer", style = MaterialTheme.typography.labelLarge)
                Slider(value = Prefs.virtualizerStrength.toFloat(), onValueChange = { AudioFx.setVirtualizer(it.toInt()) }, valueRange = 0f..1000f)
                Text("Efek audio bergantung pada dukungan perangkat dan audio session Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

