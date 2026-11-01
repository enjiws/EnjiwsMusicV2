package com.enjiws.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppScreen { Library, Online, NowPlaying }

class MainActivity : ComponentActivity() {

    var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    fun playReady(title: String, artist: String, artworkUrl: String, audioUrl: String) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        scope.launch { lyrics = fetchLyrics(artist, title) }
    }

    fun playLocal(songs: List<LocalSong>, index: Int) {
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
                    onPlayReady = { title, artist, artworkUrl, audioUrl ->
                        playReady(title, artist, artworkUrl, audioUrl)
                    }
                )
                AppScreen.NowPlaying -> NowPlayingScreenFromController(
                    controller = controller,
                    lyrics = lyrics,
                    onBack = { screen = previousScreen },
                    onMore = { }
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
