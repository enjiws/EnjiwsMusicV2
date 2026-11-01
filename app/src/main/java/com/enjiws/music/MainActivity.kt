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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen { Library, Online, Playlist, NowPlaying }

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
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }

    // Status mini player (dipoll dari controller tiap 500 ms).
    var isPlaying by remember { mutableStateOf(false) }
    var nowTitle by remember { mutableStateOf("") }
    var nowArtist by remember { mutableStateOf("") }

    // Antrean lagu online: hasil pencarian terakhir + posisi lagu yang sedang jalan.
    // -1 artinya tidak ada antrean online aktif (mis. sedang memutar lagu lokal).
    var onlineQueue by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var onlineIndex by remember { mutableStateOf(-1) }

    // State pencarian online diangkat ke sini supaya tetap ada walau layar Online
    // sedang tidak tampil (mis. saat di Now Playing). Inilah yang membuat teks
    // pencarian dan hasilnya tidak hilang lagi saat pindah layar.
    val onlineQueryState = remember { mutableStateOf("") }
    val onlineSelectedState = remember { mutableStateOf(setOf(Source.YouTube, Source.Apple, Source.Audius, Source.Piped)) }
    val onlineResultsState = remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    val onlineStatusState = remember { mutableStateOf("Cari lagu dari YouTube, Apple Music, Audius, dan Piped.") }

    // Izin akses musik (untuk tab Lokal).
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
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
        screen = if (screen == AppScreen.NowPlaying) previousScreen else AppScreen.Library
    }

    // Poll status pemutar untuk mini player.
    LaunchedEffect(controller) {
        while (true) {
            val c = controller
            if (c != null) {
                isPlaying = c.isPlaying
                nowTitle = c.mediaMetadata.title?.toString().orEmpty()
                nowArtist = c.mediaMetadata.artist?.toString().orEmpty()
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
        scope.launch { lyrics = fetchLyrics(track.artist, track.title) }
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
        scope.launch { lyrics = fetchLyrics(song.artist, song.title) }
    }

    // Saat satu lagu online habis, lanjut otomatis ke hasil berikutnya.
    // Kalau satu lagu gagal dibuka, loncat ke lagu sesudahnya (bukan berhenti).
    // Kalau antrean habis, ambil lagu terkait dari lagu terakhir dan lanjutkan
    // (radio otomatis): YouTube via related NewPipe (jatuh ke Piped kalau kena
    // bot check), Piped via related Piped. Lagu yang sudah ada di antrean
    // tidak diulang.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED) return
                val q = onlineQueue
                val i = onlineIndex
                if (i < 0) return
                scope.launch {
                    for (j in i + 1 until q.size) {
                        try {
                            val url = resolveTrackUrl(q[j])
                            playOnline(q, j, url)
                            return@launch
                        } catch (e: Exception) {
                            // Lagu ini gagal dibuka, coba lagu berikutnya.
                        }
                    }
                    // Antrean habis: lanjut ke lagu terkait dari lagu terakhir.
                    val last = q.getOrNull(i)
                    if (last == null) {
                        onlineIndex = -1
                        return@launch
                    }
                    val related = try {
                        fetchRelatedTracks(last).filter { r -> q.none { isSameSong(it, r) } }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (related.isEmpty()) {
                        onlineIndex = -1
                        return@launch
                    }
                    val newQueue = q + related
                    for (j in q.size until newQueue.size) {
                        try {
                            val url = resolveTrackUrl(newQueue[j])
                            playOnline(newQueue, j, url)
                            return@launch
                        } catch (e: Exception) {
                            // Lagu terkait ini gagal dibuka, coba berikutnya.
                        }
                    }
                    onlineIndex = -1
                }
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    Scaffold(
        bottomBar = {
            if (screen != AppScreen.NowPlaying) {
                Column {
                    if (nowTitle.isNotBlank()) {
                        MiniPlayerBar(
                            title = nowTitle,
                            artist = nowArtist,
                            isPlaying = isPlaying,
                            onToggle = {
                                val c = controller
                                if (c != null) {
                                    if (c.isPlaying) c.pause() else c.play()
                                }
                            },
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
        Column(Modifier.padding(padding)) {
            when (screen) {
                AppScreen.Library -> LibraryScreen(
                    hasPermission = audioGranted,
                    onRequestPermission = {
                        audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    },
                    onPlayLocal = { songs, index -> playLocal(songs, index) }
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
                AppScreen.NowPlaying -> NowPlayingScreenFromController(
                    controller = controller,
                    lyrics = lyrics,
                    onBack = { screen = previousScreen },
                    onMore = { },
                    favoriteTrack = if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null,
                    isFavorite = (if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null)?.let { PlaylistStore.isFavorite(it) } ?: false,
                    onFavoriteToggle = {
                        val t = if (onlineIndex >= 0) onlineQueue.getOrNull(onlineIndex) else null
                        if (t != null) PlaylistStore.toggleFavorite(t)
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        color = Color(0xFF1B2B1E),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { onOpen() }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(artist, color = Color(0xFF9AA99D), fontSize = 12.sp, maxLines = 1)
            }
            TextButton(onClick = onToggle) {
                Text(if (isPlaying) "PAUSE" else "PLAY", color = Color(0xFF8AFF00), fontWeight = FontWeight.Bold)
            }
        }
    }
}
