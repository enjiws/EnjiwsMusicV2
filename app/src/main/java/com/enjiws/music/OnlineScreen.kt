package com.enjiws.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    val storeUrl: String = "",
    val pipedId: String = ""
)

enum class Source { YouTube, SoundCloud, Apple, Audius, Piped }

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
                soundcloudUrl = if (source == "SoundCloud") item.getUrl() else ""
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
    track.soundcloudUrl.isNotBlank() -> resolveStreamAudio(ServiceList.SoundCloud, track.soundcloudUrl)
    track.youtubeUrl.isNotBlank() -> resolveStreamAudio(ServiceList.YouTube, track.youtubeUrl)
    track.pipedId.isNotBlank() -> resolvePipedStream(track.pipedId)
    else -> track.audioUrl
}

private suspend fun searchSource(source: Source, query: String): List<OnlineTrack> = when (source) {
    Source.YouTube -> searchNewPipe(ServiceList.YouTube, query, "YouTube")
    Source.SoundCloud -> searchNewPipe(ServiceList.SoundCloud, query, "SoundCloud")
    Source.Apple -> searchITunes(query)
    Source.Audius -> searchAudius(query)
    Source.Piped -> searchPiped(query)
}

private fun Source.displayName(): String = when (this) {
    Source.YouTube -> "YouTube"
    Source.SoundCloud -> "SoundCloud"
    Source.Apple -> "Apple Music"
    Source.Audius -> "Audius"
    Source.Piped -> "Piped"
}

@Composable
fun OnlineScreen(queryState: MutableState<String>, selectedState: MutableState<Set<Source>>, resultsState: MutableState<List<OnlineTrack>>, statusState: MutableState<String>, onPlayReady: (queue: List<OnlineTrack>, index: Int, url: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State pencarian diangkat ke MainScreen supaya tidak hilang saat pindah layar.
    // Di sini tinggal dipakai lewat delegasi; sisanya (kode pencarian, putar, unduh)
    // tidak berubah sama sekali.
    var query by queryState
    var selected by selectedState
    var results by resultsState
    var status by statusState
    var searching by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    fun toggle(source: Source) {
        selected = if (source in selected) selected - source else selected + source
    }

    fun searchAll() {
        val q = query.trim()
        val providers = selected.toList()
        if (q.isBlank()) {
            status = "Tulis judul lagu / nama artis dulu."
            return
        }
        if (providers.isEmpty()) {
            status = "Pilih minimal satu sumber dulu."
            return
        }
        scope.launch {
            searching = true
            status = "Mencari di ${providers.size} sumber secara bersamaan..."
            try {
                val responses = supervisorScope {
                    providers.map { source ->
                        async {
                            source to runCatching {
                                withTimeout(22_000L) { searchSource(source, q) }
                            }
                        }
                    }.awaitAll()
                }
                val found = responses.flatMap { (_, result) -> result.getOrDefault(emptyList()) }
                val failed = responses.filter { it.second.isFailure }.map { it.first.displayName() }
                // Urutkan hasil yang paling relevan lebih dulu, tetapi pertahankan
                // entri dari sumber berbeda agar pengguna bisa memilih versi stream-nya.
                results = found.sortedWith(
                    compareByDescending<OnlineTrack> { it.title.equals(q, ignoreCase = true) }
                        .thenByDescending { it.title.contains(q, ignoreCase = true) }
                        .thenByDescending { it.audioUrl.isNotBlank() || it.youtubeUrl.isNotBlank() || it.soundcloudUrl.isNotBlank() || it.pipedId.isNotBlank() }
                )
                val counts = responses.mapNotNull { (source, result) ->
                    result.getOrNull()?.let { "${source.displayName()}: ${it.size}" }
                }
                status = when {
                    found.isEmpty() && failed.size == providers.size -> "Semua sumber gagal diakses. Periksa koneksi atau coba lagi nanti."
                    found.isEmpty() -> "Tidak ada hasil untuk \"$q\"." + if (failed.isNotEmpty()) " Gagal: ${failed.joinToString()}" else ""
                    else -> "Ditemukan ${found.size} hasil · ${counts.joinToString(" · ")}" +
                        if (failed.isNotEmpty()) " · Gagal: ${failed.joinToString()}" else ""
                }
            } finally {
                searching = false
            }
        }
    }

    fun playTrack(track: OnlineTrack, index: Int) {
        scope.launch {
            try {
                if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank() && track.soundcloudUrl.isBlank() && track.pipedId.isBlank()) {
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
                    track.soundcloudUrl.isNotBlank() -> resolveStreamAudioDetailed(ServiceList.SoundCloud, track.soundcloudUrl)
                    track.youtubeUrl.isNotBlank() -> resolveStreamAudioDetailed(ServiceList.YouTube, track.youtubeUrl)
                    track.pipedId.isNotBlank() -> resolvePipedStreamDetailed(track.pipedId)
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Online", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Judul lagu / artis...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))

        // Chip sumber dalam satu baris geser horizontal (bukan 2 baris),
        // supaya area pencarian lebih ringkas.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = { toggle(Source.YouTube) }, modifier = Modifier.height(34.dp)) {
                Text("YouTube", color = if (Source.YouTube in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.SoundCloud) }, modifier = Modifier.height(34.dp)) {
                Text("SoundCloud", color = if (Source.SoundCloud in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.Apple) }, modifier = Modifier.height(34.dp)) {
                Text("Apple", color = if (Source.Apple in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.Audius) }, modifier = Modifier.height(34.dp)) {
                Text("Audius", color = if (Source.Audius in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { toggle(Source.Piped) }, modifier = Modifier.height(34.dp)) {
                Text("Piped", color = if (Source.Piped in selected) Color(0xFF8AFF00) else Color(0xFF9AA99D), fontWeight = FontWeight.Bold)
            }
        }

        Button(onClick = { searchAll() }, enabled = !searching, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text(if (searching) "MENCARI..." else "CARI")
        }
        Spacer(Modifier.height(5.dp))
        // Status hanya tampil kalau ada isinya; teks petunjuk permanen
        // dihapus supaya area pencarian tidak ramai.
        if (status.isNotBlank()) {
            Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
        }

        // Sisa tinggi layar diberikan ke daftar agar tidak tertutup mini-player/navigation bar.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
        ) {
            itemsIndexed(results) { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { playTrack(track, index) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtThumb(track.artworkUrl, 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        // Chip sumber (YouTube/Piped/dll) supaya asal lagu
                        // langsung terlihat, bukan cuma teks di subjudul.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (track.source.isNotBlank()) {
                                Box(
                                    Modifier.border(1.dp, Color(0xFF8AFF00), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(track.source, color = Color(0xFF8AFF00), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "${track.artist}  ·  ${if (track.durationSeconds > 0) "${track.durationSeconds / 60}:${(track.durationSeconds % 60).toString().padStart(2, '0')}  ·  " else ""}${if (track.audioUrl.isBlank() && track.youtubeUrl.isBlank() && track.soundcloudUrl.isBlank() && track.pipedId.isBlank()) "BUKA LAGU" else "PUTAR"}",
                                color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    TextButton(onClick = {
                        val added = PlaylistStore.toggleFavorite(track)
                        status = if (added) "Ditambahkan ke Favorit: ${track.title}" else "Dihapus dari Favorit: ${track.title}"
                    }) {
                        val fav = PlaylistStore.isFavorite(track)
                        Text(
                            if (fav) "♥" else "♡",
                            color = if (fav) Color(0xFFFF5252) else Color(0xFF9AA99D),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { addTarget = track }) {
                        Text("+", color = Color(0xFF8AFF00), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    val canDownload = track.audioUrl.isNotBlank() || track.youtubeUrl.isNotBlank() || track.soundcloudUrl.isNotBlank() || track.pipedId.isNotBlank()
                    var menuExpanded by remember(track) { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuExpanded = true }) {
                            Text("⋮", color = Color(0xFF8AFF00), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unduh") },
                                enabled = canDownload,
                                onClick = {
                                    menuExpanded = false
                                    downloadTrack(track)
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF253129))
            }
        }
    }

    val target = addTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("Tambah ke playlist") },
            text = {
                Column {
                    Text(target.title, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        val added = PlaylistStore.toggleFavorite(target)
                        status = if (added) "Ditambahkan ke Favorit: ${target.title}" else "Dihapus dari Favorit: ${target.title}"
                        addTarget = null
                    }) {
                        Text(if (PlaylistStore.isFavorite(target)) "♥ Hapus dari Favorit" else "♡ Tambah ke Favorit", color = Color(0xFF8AFF00))
                    }
                    PlaylistStore.playlists.forEach { pl ->
                        TextButton(onClick = {
                            PlaylistStore.addToPlaylist(pl.id, target)
                            status = "Ditambahkan ke playlist \"${pl.name}\"."
                            addTarget = null
                        }) {
                            Text("Tambah ke \"${pl.name}\"", color = Color(0xFF8AFF00))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Nama playlist baru...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            val pl = PlaylistStore.createPlaylist(newPlaylistName.trim())
                            PlaylistStore.addToPlaylist(pl.id, target)
                            status = "Ditambahkan ke playlist \"${pl.name}\"."
                            newPlaylistName = ""
                            addTarget = null
                        }
                    }) {
                        Text("BUAT & TAMBAH", color = Color(0xFF8AFF00), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { addTarget = null }) { Text("BATAL", color = Color(0xFF9AA99D)) }
            }
        )
    }
    }
}
