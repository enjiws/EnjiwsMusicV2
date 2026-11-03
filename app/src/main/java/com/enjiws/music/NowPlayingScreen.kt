package com.enjiws.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

enum class RepeatMode { OFF, ALL, ONE }

@Composable
fun NowPlayingScreen(
    title: String,
    artist: String,
    artworkUrl: String,
    lyrics: List<LyricLine>,
    plainLyrics: String = "",
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    favoriteTrack: OnlineTrack? = null,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onQueue: () -> Unit = {},
) {
    val currentLineIndex = remember(lyrics, positionMs) {
        if (lyrics.isEmpty()) -1
        else lyrics.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    Box(Modifier.fillMaxSize()) {
        NowPlayingBackground()

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            // Header: kembali | Now Playing / Sedang Diputar | ...
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassGlyphButton(onClick = onBack, glyph = "‹", fontSize = 26.sp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now Playing", color = Color.White, fontSize = 15.sp, fontFamily = FontFamily.Serif)
                    Text("Sedang Diputar", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                if (favoriteTrack != null) {
                    TextButton(onClick = onFavoriteToggle) {
                        Text(
                            if (isFavorite) "♥" else "♡",
                            color = if (isFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                GlassGlyphButton(onClick = onMore, glyph = "•••", fontSize = 16.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Piringan vinyl berputar (menggantikan thumbnail kotak besar).
            // Berputar saat lagu jalan, berhenti saat pause. Cover lagu jadi
            // label tengah piringan; kalau tidak ada cover, label hijau neon.
            SpinningVinyl(artworkUrl = artworkUrl, isPlaying = isPlaying, sizeDp = 176)

            Spacer(Modifier.height(12.dp))

            Text(
                title.ifBlank { "Unknown title" },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(2.dp))
            Text(
                artist.ifBlank { "Unknown artist" },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(Modifier.height(12.dp))

            // Lirik: tersinkron (7 baris di sekitar baris aktif) kalau ada;
            // kalau tidak, tampilkan lirik biasa yang bisa digulir; kalau
            // tidak ada sama sekali, tampilkan keterangan.
            if (lyrics.isNotEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val start = (currentLineIndex - 3).coerceAtLeast(0)
                    val end = (start + 7).coerceAtMost(lyrics.size)
                    for (i in start until end) {
                        val distance = kotlin.math.abs(i - currentLineIndex)
                        val isCurrent = i == currentLineIndex
                        Text(
                            text = lyrics[i].text,
                            color = if (isCurrent) Color.White
                                else Color.White.copy(alpha = if (distance == 1) 0.55f else 0.35f),
                            fontSize = if (isCurrent) 24.sp else if (distance == 1) 17.sp else 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            } else if (plainLyrics.isNotBlank()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        plainLyrics,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("♪", color = Color(0xFF8AFF00).copy(alpha = 0.75f), fontSize = 24.sp)
                        Text("Lirik belum tersedia", color = Color.White.copy(alpha = 0.48f), fontSize = 14.sp)
                    }
                }
            }

            // Waktu + progress bar
            Text(
                "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            SmoothProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // Bar kontrol kaca: shuffle | prev | play besar | next | repeat
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlGlyphButton(
                        onClick = onShuffleToggle,
                        glyph = "⇄",
                        fontSize = 22.sp,
                        tint = if (isShuffle) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                    ControlGlyphButton(onClick = onPrev, glyph = "⏮", fontSize = 26.sp, tint = Color.White)
                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (isPlaying) "❚❚" else "▶",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    ControlGlyphButton(onClick = onNext, glyph = "⏭", fontSize = 26.sp, tint = Color.White)
                    ControlGlyphButton(
                        onClick = onRepeatToggle,
                        glyph = if (repeatMode == RepeatMode.ONE) "↻1" else "↻",
                        fontSize = 22.sp,
                        tint = if (repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.45f) else Color.White
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Bar antrean di bawah (menggantikan tombol ≡ di header):
            // diketuk untuk membuka layar Antrean.
            TextButton(onClick = onQueue, modifier = Modifier.fillMaxWidth()) {
                Text("∧ QUEUE", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SmoothProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    fun seekAt(x: Float, width: Float) {
        if (width > 0f) {
            onSeek((x.coerceIn(0f, width) / width * safeDuration).toLong())
        }
    }
    Canvas(
        modifier = modifier
            .height(30.dp)
            .pointerInput(safeDuration) {
                detectTapGestures { tap -> seekAt(tap.x, size.width.toFloat()) }
            }
            .pointerInput(safeDuration) {
                detectDragGestures(
                    onDragStart = { point -> seekAt(point.x, size.width.toFloat()) },
                    onDrag = { change, _ ->
                        seekAt(change.position.x, size.width.toFloat())
                        change.consume()
                    }
                )
            }
    ) {
        val trackHeight = 5.dp.toPx()
        val cy = size.height / 2f
        val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        val playedWidth = size.width * progress
        drawRoundRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        if (playedWidth > 0f) {
            drawRoundRect(
                color = Color(0xFF8AFF00),
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(playedWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }
        drawCircle(
            color = Color(0xFF8AFF00),
            radius = 5.dp.toPx(),
            center = Offset(playedWidth.coerceIn(5.dp.toPx(), size.width - 5.dp.toPx()), cy)
        )
    }
}

// Piringan vinyl berputar untuk layar Now Playing. Berputar dengan kecepatan
// konstan saat lagu jalan (satu putaran 14 detik) dan berhenti di posisi
// terakhir saat pause. Kilau cahaya di atasnya tidak ikut berputar supaya
// putarannya terlihat, dan satu titik kecil di permukaan piringan jadi
// penanda putaran saat label tengahnya simetris.
@Composable
private fun SpinningVinyl(artworkUrl: String, isPlaying: Boolean, sizeDp: Int) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            rotation.animateTo(
                rotation.value + 360f,
                animationSpec = tween(durationMillis = 14000, easing = LinearEasing)
            )
        }
    }
    val labelSize = (sizeDp * 0.38f).dp
    Box(
        Modifier.size(sizeDp.dp).shadow(28.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation.value },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF141414)))
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxR = size.minDimension / 2
                for (f in listOf(0.95f, 0.85f, 0.75f, 0.65f)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = maxR * f,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.padding(top = 10.dp).size(6.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.4f))
                )
            }
            Box(
                Modifier.size(labelSize).clip(CircleShape).background(Color(0xFF8AFF00)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("♪", color = Color(0xFF050805), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF050805)))
        }
        Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.32f, size.height * 0.28f),
                    radius = size.minDimension * 0.5f
                )
            )
        }
    }
}

// Latar gradasi ungu-biru-pink (cahaya nebula lembut di atas dasar gelap).
@Composable
private fun NowPlayingBackground() {
    Box(Modifier.fillMaxSize().background(Color(0xFF050805))) {
        Box(
            Modifier.size(360.dp).offset((-80).dp, (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF00C853).copy(alpha = 0.55f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier.size(340.dp).offset(180.dp, 120.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF245A25).copy(alpha = 0.28f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier.size(300.dp).offset((-100).dp, 420.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF286B28).copy(alpha = 0.30f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier.size(260.dp).offset(200.dp, 560.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF006B45).copy(alpha = 0.28f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
    }
}

@Composable
fun GlassGlyphButton(onClick: () -> Unit, glyph: String, fontSize: TextUnit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlGlyphButton(onClick: () -> Unit, glyph: String, fontSize: TextUnit, tint: Color) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(glyph, color = tint, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

// Pembungkus siap pakai: membaca posisi, status putar, shuffle, dan repeat
// dari MediaController tiap 500 ms.
@Composable
fun NowPlayingScreenFromController(
    controller: MediaController?,
    lyrics: List<LyricLine>,
    plainLyrics: String = "",
    onBack: () -> Unit,
    onMore: () -> Unit,
    onNext: () -> Unit = { controller?.seekToNext() },
    onPrev: () -> Unit = { controller?.seekToPrevious() },
    favoriteTrack: OnlineTrack? = null,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onQueue: () -> Unit = {},
) {
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isShuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artworkUrl by remember { mutableStateOf("") }

    LaunchedEffect(controller) {
        while (true) {
            val c = controller
            if (c != null) {
                positionMs = c.currentPosition
                durationMs = c.duration.coerceAtLeast(0)
                isPlaying = c.isPlaying
                isShuffle = c.shuffleModeEnabled
                repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                }
                val meta = c.mediaMetadata
                title = meta.title?.toString().orEmpty()
                artist = meta.artist?.toString().orEmpty()
                artworkUrl = meta.artworkUri?.toString().orEmpty()
            }
            delay(250)
        }
    }

    NowPlayingScreen(
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        lyrics = lyrics,
        plainLyrics = plainLyrics,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        isShuffle = isShuffle,
        repeatMode = repeatMode,
        onBack = onBack,
        onMore = onMore,
        onPlayPause = {
            if (controller != null) {
                if (controller.isPlaying) controller.pause() else controller.play()
            }
        },
        onNext = onNext,
        onPrev = onPrev,
        onSeek = { controller?.seekTo(it) },
        onShuffleToggle = {
            if (controller != null) controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        },
        onRepeatToggle = {
            if (controller != null) {
                controller.repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        },
        favoriteTrack = favoriteTrack,
        isFavorite = isFavorite,
        onFavoriteToggle = onFavoriteToggle,
        onQueue = onQueue,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF050805)
@Composable
private fun NowPlayingScreenPreview() {
    NowPlayingScreen(
        title = "Bintang di Surga",
        artist = "Noah",
        artworkUrl = "",
        lyrics = listOf(
            LyricLine("Di bawah langit malam", 60_000),
            LyricLine("Bintang-bintang bersinar", 70_000),
            LyricLine("Malam ini terasa indah", 80_000),
            LyricLine("Kau dan aku bersama", 90_000),
            LyricLine("Selamanya", 100_000)
        ),
        positionMs = 84_000,
        durationMs = 225_000,
        isPlaying = true,
        isShuffle = false,
        repeatMode = RepeatMode.OFF,
        onBack = {},
        onMore = {},
        onPlayPause = {},
        onNext = {},
        onPrev = {},
        onSeek = {},
        onShuffleToggle = {},
        onRepeatToggle = {}
    )
}
