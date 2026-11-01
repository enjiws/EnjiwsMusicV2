package com.enjiws.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder

data class OnlineTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val durationSeconds: Long,
    val source: String,
    val audioUrl: String = "",
    val youtubeUrl: String = "",
    val soundcloudUrl: String = "",
    val storeUrl: String = ""
)

private enum class Source { YouTube, Apple, Audius }

private suspend fun searchNewPipe(service: StreamingService, query: String, source: String): List<OnlineTrack> =
    withContext(Dispatchers.IO) {
        val handler = service.getSearchQHFactory().fromQuery(query, emptyList(), "")
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        val pageItems = extractor.getInitialPage()?.getItems().orEmpty()
        val items: List<StreamInfoItem> = pageItems.filterIsInstance<StreamInfoItem>()
        items.map { item: StreamInfoItem ->
            val thumbs = item.getThumbnails()
            val thumb = thumbs.maxByOrNull { it.getHeight() }?.getUrl()
                ?: thumbs.firstOrNull()?.getUrl() ?: ""
            OnlineTrack(
                title = item.getName() ?: "Unknown title",
                artist = item.getUploaderName() ?: "Unknown artist",
                artworkUrl = thumb,
                durationSeconds = item.getDuration(),
                source = source,
                youtubeUrl = if (source == "YouTube") item.getUrl() else "",
                soundcloudUrl = ""
            )
        }
    }

private suspend fun searchITunes(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(query, "UTF-8") + "&media=music&limit=25"
    val body = httpClient.newCall(Request.Builder().url(url).build()).execute().body?.string().orEmpty()
    val arr = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        OnlineTrack(
            title = o.optString("trackName"),
            artist = o.optString("artistName"),
            artworkUrl = o.optString("artworkUrl100").replace("100x100bb.jpg", "600x600bb.jpg"),
            durationSeconds = o.optLong("trackTimeMillis") / 1000,
            source = "Apple Music",
            audioUrl = o.optString("previewUrl"),
            storeUrl = o.optString("trackViewUrl")
        )
    }.filter { it.title.isNotBlank() }
}

private suspend fun searchAudius(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    val url = "https://discoveryprovider.audius.co/v1/tracks/search?query=" +
        URLEncoder.encode(query, "UTF-8") + "&app_name=EnjiwsMusic"
    val body = httpClient.newCall(Request.Builder().url(url).build()).execute().body?.string().orEmpty()
    val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id")
        OnlineTrack(
            title = o.optString("title"),
            artist = o.optJSONObject("user")?.optString("name").orEmpty(),
            artworkUrl = o.optJSONObject("artwork")?.optString("1000x1000").orEmpty(),
            durationSeconds = o.optLong("duration"),
            source = "Audius",
            audioUrl = if (id.isNotBlank()) "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=EnjiwsMusic" else ""
        )
    }.filter { it.title.isNotBlank() }
}

@Composable
fun ArtThumb(url: String, sizeDp: Int) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier.size(sizeDp.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF253129)),
            contentAlignment = Alignment.Center
        ) {
            Text("♪", color = Color(0xFF9AA99D), fontSize = 20.sp)
        }
    }
}


// Terjemahkan pesan error teknis (biasanya dari NewPipe, berbahasa Inggris)
// jadi bahasa Indonesia yang dimengerti pengguna.
fun friendlyError(e: Exception): String {
    val msg = e.message ?: ""
    return when {
        msg.contains("Could not get any stream") ->
            "lagu ini tidak menyediakan link audio yang bisa diambil"
        msg.contains("HLS/DASH") ->
            "lagu ini hanya tersedia sebagai stream HLS/DASH yang belum didukung"
        msg.contains("429") || msg.contains("Too Many Requests", ignoreCase = true) ->
            "server membatasi permintaan, coba lagi beberapa saat"
        msg.contains("not a bot", ignoreCase = true) ->
            "YouTube mengira aplikasi ini bot (sering karena VPN aktif) — coba matikan VPN lalu putar lagi"
        msg.contains("needs to be reloaded", ignoreCase = true) ->
            "YouTube gagal memuat halaman video (mesin YouTube aplikasi ketinggalan — coba lagi setelah update)"
        msg.isBlank() -> "coba lagi"
        else -> msg
    }
}

// Ambil URL audio yang bisa diputar untuk satu lagu online.
suspend fun resolveTrackUrl(track: OnlineTrack): String = when {
    track.youtubeUrl.isNotBlank() -> resolveStreamAudio(ServiceList.YouTube, track.youtubeUrl)
    else -> track.audioUrl
}

@Composable
fun OnlineScreen(onPlayReady: (queue: List<OnlineTrack>, index: Int, url: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf(Source.YouTube, Source.Apple, Source.Audius)) }
    var results by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var status by remember { mutableStateOf("Cari lagu dari YouTube, Apple Music, dan Audius.") }
    var searching by remember { mutableStateOf(false) }

    fun toggle(source: Source) {
        selected = if (source in selected) selected - source else selected + source
    }

    fun searchAll() {
        val q = query.trim()
        if (q.isBlank()) {
            status = "Tulis judul lagu / nama artis dulu."
            return
        }
        if (selected.isEmpty()) {
            status = "Pilih minimal satu sumber dulu."
            return
        }
        scope.launch {
            searching = true
            status = "Mencari \"$q\"..."
            val found = mutableListOf<OnlineTrack>()
            val failed = mutableListOf<String>()
            if (Source.YouTube in selected) {
                runCatching { found += searchNewPipe(ServiceList.YouTube, q, "YouTube") }
                    .onFailure { failed += "YouTube" }
            }
            if (Source.Apple in selected) {
                runCatching { found += searchITunes(q) }.onFailure { failed += "Apple Music" }
            }
            if (Source.Audius in selected) {
                runCatching { found += searchAudius(q) }.onFailure { failed += "Audius" }
            }
            results = found
            searching = false
            status = if (found.isEmpty()) {
                "Tidak ada hasil untuk \"$q\"."
            } else {
                "Ditemukan ${found.size} lagu." + if (failed.isNotEmpty()) " (${failed.joinToString()} gagal.)" else ""
            }
        }
    }

    fun playTrack(track: OnlineTrack, index: Int) {
        scope.launch {
            try {
                if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank()) {
                    if (track.storeUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.storeUrl)))
                    }
                    return@launch
                }
                status = "Memuat: ${track.title}..."
                val url = resolveTrackUrl(track)
                onPlayReady(results, index, url)
                status = "Memutar: ${track.title}"
            } catch (e: Exception) {
                status = "Tidak dapat diputar: ${friendlyError(e)}"
            }
        }
    }

    fun downloadTrack(track: OnlineTrack) {
        scope.launch {
            try {
                status = "Menyiapkan unduhan: ${track.title}..."
                val (url, ext) = when {
                    track.youtubeUrl.isNotBlank() -> resolveStreamAudioDetailed(ServiceList.YouTube, track.youtubeUrl)
                    track.audioUrl.isNotBlank() -> track.audioUrl to "mp3"
                    else -> {
                        status = "Lagu ini tidak bisa diunduh."
                        return@launch
                    }
                }
                enqueueDownload(context, track.title, track.artist, url, ext)
                status = "Mengunduh: ${track.title} (cek notifikasi, tersimpan di Musik/ENJIWS)"
            } catch (e: Exception) {
                status = "Gagal mengunduh: ${friendlyError(e)}"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Online", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Judul lagu / artis...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Row {
            TextButton(onClick = { toggle(Source.YouTube) }) {
                Text("YouTube", color = if (Source.YouTube in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.Apple) }) {
                Text("Apple", color = if (Source.Apple in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.Audius) }) {
                Text("Audius", color = if (Source.Audius in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
        }

        Button(onClick = { searchAll() }, enabled = !searching, modifier = Modifier.fillMaxWidth()) {
            Text(if (searching) "MENCARI..." else "CARI")
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp)
        Text("Ketuk lagu untuk memutar (otomatis lanjut ke hasil berikutnya), tombol UNDUH untuk menyimpan ke folder Musik/ENJIWS.", color = Color(0xFF9AA99D), fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results) { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { playTrack(track, index) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtThumb(track.artworkUrl, 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            "${track.artist}  ·  ${if (track.durationSeconds > 0) "${track.durationSeconds / 60}:${(track.durationSeconds % 60).toString().padStart(2, '0')}  ·  " else ""}${track.source}${if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank()) " · BUKA LAGU" else " · PUTAR"}",
                            color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1
                        )
                    }
                    if (track.audioUrl.isNotBlank() || track.youtubeUrl.isNotBlank()) {
                        TextButton(onClick = { downloadTrack(track) }) {
                            Text("UNDUH", color = Color(0xFF8AFF00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF253129))
            }
        }
    }
}
