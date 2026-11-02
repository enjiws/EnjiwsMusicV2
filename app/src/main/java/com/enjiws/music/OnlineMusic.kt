package com.enjiws.music

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class OnlineTrack(
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationSeconds: Int,
    val source: String,
    val storeUrl: String = "",
    val youtubeUrl: String = "",
    val soundcloudUrl: String = "",
    val artworkUrl: String = ""
)

private fun JSONObject.str(key: String): String = optString(key).let { if (it == "null") "" else it }

@Composable
fun OnlineMusicScreen(controllerProvider: () -> MediaController?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var catalogQuery by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("415f93ab") }
    var streamUrl by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var status by remember { mutableStateOf("Cari katalog global: SoundCloud, YouTube, Apple Music, Jamendo, dan Audius.") }
    var busy by remember { mutableStateOf(false) }

    fun playUrl(url: String, title: String, artist: String, artwork: String = "") {
        val player = controllerProvider()
        if (player == null) {
            status = "Pemutar belum siap. Coba lagi sebentar."
            return
        }
        try {
            val meta = MediaMetadata.Builder()
                .setTitle(title.ifBlank { "Online audio" })
                .setArtist(artist.ifBlank { "ENJIWS ONLINE" })
            if (artwork.startsWith("http")) meta.setArtworkUri(Uri.parse(artwork))
            val item = MediaItem.Builder().setUri(url).setMediaMetadata(meta.build()).build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
            status = "Memutar: $title"
        } catch (e: Exception) {
            status = "URL tidak dapat diputar: ${e.message ?: "format tidak didukung"}"
        }
    }

    // Unduh lagu ke folder Musik/ENJIWS lewat DownloadManager (jalan di
    // background, jadi tetap bisa sambil memutar lagu lain).
    fun downloadTrack(track: OnlineTrack) {
        scope.launch {
            try {
                status = "Menyiapkan unduhan: ${track.title}..."
                val (url, ext) = when {
                    track.soundcloudUrl.isNotBlank() ->
                        resolveStreamAudioDetailed(ServiceList.SoundCloud, track.soundcloudUrl)
                    track.youtubeUrl.isNotBlank() ->
                        resolveStreamAudioDetailed(ServiceList.YouTube, track.youtubeUrl)
                    track.audioUrl.isNotBlank() -> track.audioUrl to "mp3"
                    else -> {
                        status = "Lagu ini tidak bisa diunduh."
                        return@launch
                    }
                }
                val fileName = sanitizeFileName("${track.artist} - ${track.title}") + ".$ext"
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(track.title)
                    .setDescription("ENJIWS Music")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "ENJIWS/$fileName")
                dm.enqueue(request)
                status = "Mengunduh: ${track.title} (cek notifikasi, tersimpan di Musik/ENJIWS)"
            } catch (e: Exception) {
                status = "Gagal mengunduh: ${e.message ?: "coba lagi"}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0D120F)).padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text("ENJIWS / ONLINE", color = Color(0xFF91D6A0), fontSize = 11.sp, letterSpacing = 1.8.sp)
        Text("Music beyond.", color = Color(0xFFF1F5F1), fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        Text("Streaming dan lirik, dalam satu ruang.", color = Color(0xFF9AA99D), fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Text("STREAM DARI URL", color = Color(0xFF91D6A0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(7.dp))
                InputPanel(value = streamUrl, onValueChange = { streamUrl = it }, hint = "https://.../audio.mp3")
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = {
                        val clean = streamUrl.trim()
                        if (clean.startsWith("https://") || clean.startsWith("http://")) {
                            playUrl(clean, "Online stream", "URL AUDIO")
                        } else status = "Masukkan URL audio yang diawali http:// atau https://."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF91D6A0)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("PUTAR URL", color = Color(0xFF0D120F), fontWeight = FontWeight.Bold) }
                Text("Gunakan direct link audio yang dapat diakses publik (MP3/AAC atau stream yang didukung).", color = Color(0xFF9AA99D), fontSize = 10.sp)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFF253129))
                Spacer(Modifier.height(16.dp))
                Text("GLOBAL MUSIC DISCOVERY", color = Color(0xFF91D6A0), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(7.dp))
                InputPanel(value = catalogQuery, onValueChange = { catalogQuery = it }, hint = "Cari judul, artis, atau genre")
                Spacer(Modifier.height(7.dp))
                InputPanel(value = clientId, onValueChange = { clientId = it }, hint = "Jamendo client_id")
                Text("YouTube, Jamendo, dan Audius bisa diputar; Apple Music membuka halaman lagu. ID Jamendo bisa diganti.", color = Color(0xFF9AA99D), fontSize = 10.sp)
                Spacer(Modifier.height(7.dp))
                Button(
                    enabled = !busy && catalogQuery.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Mencari SoundCloud, YouTube, Apple Music, Jamendo, dan Audius..."
                            try {
                                val q = catalogQuery.trim()
                                val soundcloudResult = runCatching { searchSoundCloud(q) }
                                val youtubeResult = runCatching { searchYouTube(q) }
                                val appleResult = runCatching { searchAppleCatalog(q) }
                                val jamendoResult = runCatching { if (clientId.isBlank()) emptyList() else searchJamendo(q, clientId.trim()) }
                                val audiusResult = runCatching { searchAudius(q) }
                                tracks = (soundcloudResult.getOrDefault(emptyList()) + youtubeResult.getOrDefault(emptyList()) + appleResult.getOrDefault(emptyList()) +
                                    jamendoResult.getOrDefault(emptyList()) + audiusResult.getOrDefault(emptyList()))
                                    .distinctBy { it.title.lowercase() + ":" + it.artist.lowercase() + ":" + it.source }
                                status = when {
                                    tracks.isNotEmpty() -> "Ditemukan ${tracks.size} hasil. SoundCloud, YouTube, Jamendo, dan Audius dapat diputar; Apple Music membuka halaman lagu."
                                    soundcloudResult.isFailure && youtubeResult.isFailure && appleResult.isFailure && jamendoResult.isFailure && audiusResult.isFailure -> "Semua katalog gagal diakses. Periksa koneksi internet."
                                    else -> "Belum ada hasil. Coba kata kunci lain."
                                }
                            } finally { busy = false }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20382A)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "MENCARI..." else "CARI DI SEMUA KATALOG", color = Color(0xFF91D6A0)) }
                Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp)
                Text("Ketuk ikon unduh pada lagu untuk menyimpan file ke folder Musik/ENJIWS.", color = Color(0xFF9AA99D), fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(tracks) { track ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (track.soundcloudUrl.isNotBlank()) {
                            scope.launch {
                                status = "Mengambil audio SoundCloud: ${track.title}..."
                                try {
                                    val url = resolveStreamAudio(ServiceList.SoundCloud, track.soundcloudUrl)
                                    playUrl(url, track.title, track.artist, track.artworkUrl)
                                } catch (e: Exception) {
                                    status = "Gagal ambil audio SoundCloud: ${e.message ?: "coba lagi"}"
                                }
                            }
                        } else if (track.youtubeUrl.isNotBlank()) {
                            scope.launch {
                                status = "Mengambil audio YouTube: ${track.title}..."
                                try {
                                    val url = resolveStreamAudio(ServiceList.YouTube, track.youtubeUrl)
                                    playUrl(url, track.title, track.artist, track.artworkUrl)
                                } catch (e: Exception) {
                                    status = "Gagal ambil audio YouTube: ${e.message ?: "coba lagi / update NewPipeExtractor"}"
                                }
                            }
                        } else if (track.audioUrl.isNotBlank()) {
                            playUrl(track.audioUrl, track.title, track.artist, track.artworkUrl)
                        } else if (track.storeUrl.startsWith("https://")) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.storeUrl)))
                            }.onFailure { status = "Tidak dapat membuka halaman lagu." }
                        }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteArtThumb(track.artworkUrl, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            "${track.artist}  ·  ${if (track.durationSeconds > 0) "${track.durationSeconds / 60}:${(track.durationSeconds % 60).toString().padStart(2, '0')}  ·  " else ""}${track.source}${if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank() && track.soundcloudUrl.isBlank()) " · BUKA LAGU" else " · PUTAR"}",
                            color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1
                        )
                    }
                    if (track.audioUrl.isNotBlank() || track.youtubeUrl.isNotBlank() || track.soundcloudUrl.isNotBlank()) {
                        TextButton(onClick = { downloadTrack(track) }) {
                            Text("UNDUH", color = Color(0xFF91D6A0), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF253129))
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("Lirik tersinkron tampil di layar Now Playing (tombol LIRIK). Sumber lirik: LRCLIB.", color = Color(0xFF9AA99D), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun InputPanel(value: String, onValueChange: (String) -> Unit, hint: String) {
    Surface(color = Color(0xFF17221B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 13.dp, vertical = 14.dp)) {
            if (value.isEmpty()) Text(hint, color = Color(0xFF718176), fontSize = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFF1F5F1), fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private suspend fun searchYouTube(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val yt = ServiceList.YouTube
    val handler = yt.searchQHFactory.fromQuery(
        query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""
    )
    SearchInfo.getInfo(yt, handler).relatedItems
        .filterIsInstance<StreamInfoItem>()
        .take(20)
        .map {
            OnlineTrack(
                title = it.name,
                artist = it.uploaderName ?: "YouTube",
                audioUrl = "",
                durationSeconds = it.duration.toInt().coerceAtLeast(0),
                source = "YouTube",
                youtubeUrl = it.url,
                artworkUrl = it.thumbnails.maxByOrNull { img -> img.height }?.url.orEmpty()
            )
        }
}

// URL stream kedaluwarsa dalam beberapa jam: selalu ambil ulang saat lagu diputar.
private suspend fun resolveStreamAudio(service: StreamingService, pageUrl: String): String = withContext(Dispatchers.IO) {
    resolveStreamAudioDetailed(service, pageUrl).first
}

// Sama seperti resolveStreamAudio, tapi ikut mengembalikan ekstensi file
// (m4a/mp3/dll) supaya hasil unduhan tersimpan dengan format yang benar.
private suspend fun resolveStreamAudioDetailed(service: StreamingService, pageUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
    val info = StreamInfo.getInfo(service, pageUrl)
    val all = info.audioStreams
    val usable = all.filter { it.isUrl }
    val progressive = usable.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
    val best = progressive.filter { it.format == MediaFormat.M4A || it.format == MediaFormat.MP3 }
        .maxByOrNull { it.averageBitrate }
        ?: progressive.maxByOrNull { it.averageBitrate }
        ?: throw IllegalStateException(
            if (usable.isNotEmpty()) "Hanya tersedia stream HLS/DASH (butuh modul media3-exoplayer-hls)"
            else "audio=${all.size}, url=${usable.size}, hls=${!info.hlsUrl.isNullOrBlank()}, dash=${!info.dashMpdUrl.isNullOrBlank()}"
        )
    val ext = when (best.format) {
        MediaFormat.M4A -> "m4a"
        MediaFormat.MP3 -> "mp3"
        MediaFormat.WEBMA -> "webm"
        MediaFormat.OPUS -> "opus"
        else -> "mp3"
    }
    best.content to ext
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "track" }.take(100)

private suspend fun searchSoundCloud(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val sc = ServiceList.SoundCloud
    val handler = sc.searchQHFactory.fromQuery(
        query, listOf(SoundcloudSearchQueryHandlerFactory.TRACKS), ""
    )
    SearchInfo.getInfo(sc, handler).relatedItems
        .filterIsInstance<StreamInfoItem>()
        .take(20)
        .map {
            OnlineTrack(
                title = it.name,
                artist = it.uploaderName ?: "SoundCloud",
                audioUrl = "",
                durationSeconds = it.duration.toInt().coerceAtLeast(0),
                source = "SoundCloud",
                soundcloudUrl = it.url,
                artworkUrl = it.thumbnails.maxByOrNull { img -> img.height }?.url.orEmpty()
            )
        }
}

private suspend fun searchAppleCatalog(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val storefronts = listOf("us", "id", "gb", "jp", "kr")
    val collected = mutableListOf<OnlineTrack>()
    for (country in storefronts) {
        val endpoint = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=15&country=$country"
        val json = runCatching { httpGet(endpoint) }.getOrNull() ?: continue
        val results = json.optJSONArray("results") ?: continue
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val title = item.optString("trackName")
            val artist = item.optString("artistName")
            val storeUrl = item.optString("trackViewUrl")
            if (title.isNotBlank() && artist.isNotBlank() && storeUrl.startsWith("https://")) {
                collected.add(
                    OnlineTrack(
                        title = title,
                        artist = artist,
                        audioUrl = "",
                        durationSeconds = item.optLong("trackTimeMillis", 0L).div(1000L).toInt(),
                        source = "Apple Music",
                        storeUrl = storeUrl,
                        artworkUrl = item.str("artworkUrl100").replace("100x100", "300x300")
                    )
                )
            }
        }
    }
    collected.distinctBy { it.title.lowercase() + ":" + it.artist.lowercase() }
}

private suspend fun searchJamendo(query: String, clientId: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val encodedId = URLEncoder.encode(clientId, "UTF-8")
    val endpoint = "https://api.jamendo.com/v3.0/tracks/?client_id=$encodedId&format=json&limit=20&audioformat=mp32&search=$encodedQuery"
    val json = httpGet(endpoint)
    val results = json.optJSONArray("results") ?: return@withContext emptyList()
    buildList {
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val audio = item.optString("audio")
            if (audio.startsWith("http")) add(
                OnlineTrack(
                    title = item.optString("name", "Unknown title"),
                    artist = item.optString("artist_name", "Unknown artist"),
                    audioUrl = audio,
                    durationSeconds = item.optInt("duration", 0),
                    source = "Jamendo",
                    artworkUrl = item.str("image").ifBlank { item.str("album_image") }
                )
            )
        }
    }
}

private suspend fun searchAudius(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val endpoint = "https://api.audius.co/v1/tracks/search?query=$encodedQuery&limit=20&sort_method=relevant&app_name=EnjiwsMusic"
    val json = httpGet(endpoint)
    val results = json.optJSONArray("data") ?: return@withContext emptyList()
    buildList {
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank() || id == "null" || item.optBoolean("is_stream_gated", false)) continue
            val user = item.optJSONObject("user")
            val artwork = item.optJSONObject("artwork")
            add(
                OnlineTrack(
                    title = item.optString("title", "Unknown title"),
                    artist = user?.optString("name", "Unknown artist") ?: "Unknown artist",
                    audioUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=EnjiwsMusic",
                    durationSeconds = item.optInt("duration", 0),
                    source = "Audius",
                    artworkUrl = artwork?.let { a -> a.str("480x480").ifBlank { a.str("150x150") } }.orEmpty()
                )
            )
        }
    }
}

private fun httpGet(address: String): JSONObject {
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 15000
        setRequestProperty("User-Agent", "EnjiwsMusic/1.8 (https://github.com/enjiws/Enjiws-music)")
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return JSONObject(body)
    } finally {
        connection.disconnect()
    }
}
