package com.enjiws.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.schabi.newpipe.extractor.NewPipe
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen { Library, Online, Playlist, Queue, NowPlaying }

class MainActivity : ComponentActivity() {

    var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NewPipe.init(OkHttpDownloader())
        PlaylistStore.init(applicationContext)
        setContent {
            EnjiwsTheme {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null
        super.onDestroy()
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val scope = rememberCoroutineScope()

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var screen by remember { mutableStateOf(AppScreen.Library) }
    var previousScreen by remember { mutableStateOf(AppScreen.Library) }
    var queueOrigin by remember { mutableStateOf(AppScreen.NowPlaying) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var plainLyrics by remember { mutableStateOf("") }
    var lyricsJob by remember { mutableStateOf<Job?>(null) }
    // Mencegah beberapa proses next/autoplay berjalan bersamaan.
    var nextPlaybackJob by remember { mutableStateOf<Job?>(null) }

    // Status mini player (dipoll dari controller tiap 500 ms).
    var isPlaying by remember { mutableStateOf(false) }
    var nowTitle by remember { mutableStateOf("") }
    var nowArtist by remember { mutableStateOf("") }
    var nowArtwork by remember { mutableStateOf("") }
    var nowPositionMs by remember { mutableStateOf(0L) }
    var nowDurationMs by remember { mutableStateOf(0L) }

    // Antrean lagu online: hasil pencarian terakhir + posisi lagu yang sedang jalan.
    // -1 artinya tidak ada antrean online aktif (mis. sedang memutar lagu lokal).
    var onlineQueue by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var onlineIndex by remember { mutableStateOf(-1) }

    // Pulihkan metadata antrean online. URL stream di-resolve ulang saat diputar.
    LaunchedEffect(Unit) {
        val saved = QueuePersistence.restore(context)
        if (onlineQueue.isEmpty() && saved.tracks.isNotEmpty()) {
            onlineQueue = saved.tracks
            onlineIndex = saved.index
        }
    }
    LaunchedEffect(onlineQueue, onlineIndex) {
        QueuePersistence.save(context, onlineQueue, onlineIndex)
    }

    // State pencarian online diangkat ke sini supaya tetap ada walau layar Online
    // sedang tidak tampil (mis. saat di Now Playing). Inilah yang membuat teks
    // pencarian dan hasilnya tidak hilang lagi saat pindah layar.
    val onlineQueryState = remember { mutableStateOf("") }
    val onlineSelectedState = remember { mutableStateOf(setOf(Source.YouTube, Source.SoundCloud, Source.Apple, Source.Audius, Source.Piped)) }
    val onlineResultsState = remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    val onlineStatusState = remember { mutableStateOf("Cari lagu dari YouTube, SoundCloud, Apple Music, Audius, dan Piped.") }

    // Izin akses musik (untuk tab Lokal).
    val audioPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> audioGranted = granted }

    // Izin notifikasi (untuk kontrol pemutar di notifikasi, Android 13+).
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Hubungkan ke PlaybackService.
    LaunchedEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            activity.mediaController = future.get()
            controller = activity.mediaController
        }, MoreExecutors.directExecutor())
    }

    // Tombol kembali sistem: pindah layar dulu, bukan langsung keluar aplikasi.
    // Now Playing -> layar sebelumnya; Online -> Lokal; Lokal -> keluar (default sistem).
    BackHandler(enabled = screen != AppScreen.Library) {
        screen = when (screen) {
            AppScreen.NowPlaying -> previousScreen
            AppScreen.Queue -> queueOrigin
            else -> AppScreen.Library
        }
    }

    // Poll status pemutar untuk mini player.
    LaunchedEffect(controller) {
        while (true) {
            val c = controller
            if (c != null) {
                isPlaying = c.isPlaying
                nowTitle = c.mediaMetadata.title?.toString().orEmpty()
                nowArtist = c.mediaMetadata.artist?.toString().orEmpty()
                nowArtwork = c.mediaMetadata.artworkUri?.toString().orEmpty()
                nowPositionMs = c.currentPosition
                nowDurationMs = c.duration.coerceAtLeast(0)
            }
            delay(500)
        }
    }


    fun openNowPlaying() {
        previousScreen = screen
        screen = AppScreen.NowPlaying
    }

    fun playOnline(queue: List<OnlineTrack>, index: Int, resolvedUrl: String) {
        val c = controller ?: return
        val track = queue[index]
        onlineQueue = queue
        onlineIndex = index
        val item = MediaItem.Builder()
            .setUri(resolvedUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        lyricsJob?.cancel()
        lyrics = emptyList()
        plainLyrics = ""
        val request = scope.launch {
            val synced = fetchLyrics(track.artist, track.title)
            val plain = if (synced.isEmpty()) fetchPlainLyrics(track.artist, track.title).orEmpty() else ""
            if (isActive && lyricsJob === coroutineContext[Job]) {
                lyrics = synced
                plainLyrics = plain
            }
        }
        lyricsJob = request
    }

    fun playLocal(songs: List<LocalSong>, index: Int) {
        onlineIndex = -1
        val c = controller ?: return
        val items = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(song.artworkUri)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, index, 0)
        c.prepare()
        c.play()
        val song = songs[index]
        lyricsJob?.cancel()
        lyrics = emptyList()
        plainLyrics = ""
        val request = scope.launch {
            val synced = fetchLyrics(song.artist, song.title)
            val plain = if (synced.isEmpty()) fetchPlainLyrics(song.artist, song.title).orEmpty() else ""
            if (isActive && lyricsJob === coroutineContext[Job]) {
                lyrics = synced
                plainLyrics = plain
            }
        }
        lyricsJob = request
    }

    // Maju satu lagu online. Mendukung shuffle, repeat-all, dan radio otomatis
    // ketika antrean normal habis. Satu job aktif dijaga agar tap cepat atau
    // callback STATE_ENDED tidak memicu beberapa resolusi stream bersamaan.
    fun playOnlineNext() {
        if (nextPlaybackJob?.isActive == true) return
        val q = onlineQueue
        val i = onlineIndex
        if (i < 0 || q.isEmpty()) return

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val c = controller
                val repeatAll = c?.repeatMode == Player.REPEAT_MODE_ALL
                val shuffle = c?.shuffleModeEnabled == true

                val candidateIndices = when {
                    shuffle && q.size > 1 ->
                        q.indices.filter { it != i }.shuffled()
                    shuffle && repeatAll -> listOf(i)
                    repeatAll && i + 1 >= q.size -> listOf(0)
                    else -> (i + 1 until q.size).toList()
                }

                for (j in candidateIndices) {
                    try {
                        val url = resolveTrackUrl(q[j])
                        playOnline(q, j, url)
                        return@launch
                    } catch (_: Exception) {
                        // Stream gagal di-resolve; coba kandidat berikutnya.
                    }
                }

                // Repeat ALL tanpa shuffle kembali ke awal antrean.
                if (repeatAll && !shuffle && i > 0) {
                    for (j in 0 until i) {
                        try {
                            val url = resolveTrackUrl(q[j])
                            playOnline(q, j, url)
                            return@launch
                        } catch (_: Exception) {
                            // Lanjut ke kandidat berikutnya.
                        }
                    }
                }

                // Shuffle + repeat-all: bila hanya satu lagu, putar ulang.
                if (shuffle && repeatAll && q.size == 1) {
                    try {
                        val url = resolveTrackUrl(q[0])
                        playOnline(q, 0, url)
                        return@launch
                    } catch (_: Exception) {
                        // Lanjut ke fallback radio.
                    }
                }

                // Repeat OFF: pertahankan perilaku radio otomatis saat antrean habis.
                val last = q.getOrNull(i) ?: return@launch
                val related = try {
                    fetchRelatedTracks(last).filter { r -> q.none { isSameSong(it, r) } }
                } catch (_: Exception) {
                    emptyList()
                }
                if (related.isEmpty()) return@launch

                val newQueue = q + related
                for (j in q.size until newQueue.size) {
                    try {
                        val url = resolveTrackUrl(newQueue[j])
                        playOnline(newQueue, j, url)
                        return@launch
                    } catch (_: Exception) {
                        // Coba rekomendasi berikutnya.
                    }
                }
            } finally {
                nextPlaybackJob = null
            }
        }
        nextPlaybackJob = job
        job.start()
    }

    // Mundur satu lagu di antrean online; di lagu pertama, putar ulang lagu itu.
    fun playOnlinePrev() {
        val q = onlineQueue
        val i = onlineIndex
        if (i < 0) return
        val c = controller
        if (c != null && c.currentPosition > 3_000L) {
            c.seekTo(0)
            return
        }
        if (i - 1 < 0) {
            c?.seekTo(0)
            return
        }
        scope.launch {
            try {
                val url = resolveTrackUrl(q[i - 1])
                playOnline(q, i - 1, url)
            } catch (e: Exception) {
                // Lagu sebelumnya gagal dibuka, tetap di lagu sekarang.
            }
        }
    }

    // Saat satu lagu online habis, lanjut otomatis via playOnlineNext().
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                if (onlineIndex < 0) return
                playOnlineNext()
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    Scaffold(
        bottomBar = {
            if (screen != AppScreen.NowPlaying && screen != AppScreen.Queue) {
                Column {
                    if (nowTitle.isNotBlank()) {
                        MiniPlayerBar(
                            title = nowTitle,
                            artist = nowArtist,
                            artworkUrl = nowArtwork,
                            isPlaying = isPlaying,
                            progress = if (nowDurationMs > 0) (nowPositionMs.toFloat() / nowDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onToggle = {
                                val c = controller
                                if (c != null) {
                                    if (c.isPlaying) c.pause() else c.play()
                                }
                            },
                            onNext = { if (onlineIndex >= 0) playOnlineNext() else controller?.seekToNext() },
                            onPrev = { if (onlineIndex >= 0) playOnlinePrev() else controller?.seekToPrevious() },
                            onOpen = { openNowPlaying() }
                        )
                    }
                    NavigationBar(containerColor = Color(0xFF101510)) {
                        NavigationBarItem(
                            selected = screen == AppScreen.Library,
                            onClick = { screen = AppScreen.Library },
                            icon = { Text("♫", fontSize = 18.sp) },
                            label = { Text("Lokal") }
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.Online,
                            onClick = { screen = AppScreen.Online },
                            icon = { Text("◎", fontSize = 18.sp) },
                            label = { Text("Online") }
                        )
                        NavigationBarItem(
                            selected = screen == AppScreen.Playlist,
                            onClick = { screen = AppScreen.Playlist },
                            icon = { Text("▤", fontSize = 18.sp) },
                            label = { Text("Playlist") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AppScreen.Library -> LibraryScreen(
                    hasPermission = audioGranted,
                    onRequestPermission = {
                        audioPermissionLauncher.launch(audioPermission)
                    },
                    onPlayLocal = { songs, index -> playLocal(songs, index) },
                    nowTitle = nowTitle,
                    nowArtist = nowArtist,
                    isPlaying = isPlaying
                )
                AppScreen.Online -> OnlineScreen(
                    queryState = onlineQueryState,
                    selectedState = onlineSelectedState,
                    resultsState = onlineResultsState,
                    statusState = onlineStatusState,
                    onPlayReady = { queue, index, url ->
                        playOnline(queue, index, url)
                    }
                )
                AppScreen.Playlist -> PlaylistScreen(
                    onPlayReady = { queue, index, url ->
                        playOnline(queue, index, url)
                    }
                )
                AppScreen.Queue -> QueueScreen(
                    queue = onlineQueue,
                    currentIndex = onlineIndex,
                    onBack = { screen = queueOrigin },
                    onPlayReady = { queue, index, url -> playOnline(queue, index, url) },
                    onClearUpcoming = { if (onlineIndex >= 0) onlineQueue = onlineQueue.take(onlineIndex + 1) },
                    onRemoveUpcoming = { absoluteIndex ->
                        if (absoluteIndex > onlineIndex && absoluteIndex in onlineQueue.indices) {
                            onlineQueue = onlineQueue.toMutableList().also { it.removeAt(absoluteIndex) }
                        }
                    }
                )
                AppScreen.NowPlaying -> NowPlayingScreenFromController(
                    controller = controller,
                    lyrics = lyrics,
                    plainLyrics = plainLyrics,
                    onBack = { screen = previousScreen },
                    onMore = { },
                    onNext = { if (onlineIndex >= 0) playOnlineNext() else controller?.seekToNext() },
                    onPrev = { if (onlineIndex >= 0) playOnlinePrev() else controller?.seekToPrevious() },
                    favoriteTrack = if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null,
                    isFavorite = (if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null)?.let { PlaylistStore.isFavorite(it) } ?: false,
                    onFavoriteToggle = {
                        val t = if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null
                        if (t != null) PlaylistStore.toggleFavorite(t)
                    },
                    onQueue = { queueOrigin = screen; screen = AppScreen.Queue }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artist: String,
    artworkUrl: String,
    isPlaying: Boolean,
    progress: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpen: () -> Unit
) {
    // Mini player gaya referensi: kartu membulat melayang, thumbnail,
    // judul hijau, tombol jeda hijau bulat, garis progress di tepi bawah.
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B2B1E))
            .clickable { onOpen() }
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtThumb(artworkUrl, 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color(0xFF8AFF00), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(artist, color = Color(0xFF9AA99D), fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = onPrev) {
                    Text("⏮", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF8AFF00))
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isPlaying) "❚❚" else "▶", color = Color(0xFF050805), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onNext) {
                    Text("⏭", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Color(0xFF8AFF00))
                )
            }
        }
    }
}
