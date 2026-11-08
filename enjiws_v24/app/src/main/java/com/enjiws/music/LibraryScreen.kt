package com.enjiws.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LibTab { All, Artists, Albums, Folders }
enum class LibSort { Title, Artist, Album, Recent }

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAddedSec: Long,
    val folder: String,
    val uri: Uri,
    val artworkUri: Uri?
)

// Lagu lokal diubah jadi OnlineTrack supaya bisa masuk Favorit/Playlist yang
// sama dengan lagu online (dan diputar lewat jalur yang sama). audioUrl-nya
// adalah content URI MediaStore, yang bisa diputar ExoPlayer.
fun LocalSong.toOnlineTrack(): OnlineTrack = OnlineTrack(
    title = title,
    artist = artist,
    artworkUrl = artworkUri?.toString().orEmpty(),
    durationSeconds = durationMs / 1000,
    source = "Lokal",
    audioUrl = uri.toString()
)

// Baca semua file musik di HP lewat MediaStore. Butuh izin READ_MEDIA_AUDIO
// (diminta dari MainActivity sebelum layar ini memuat daftar).
suspend fun loadLocalSongs(context: Context): List<LocalSong> = withContext(Dispatchers.IO) {
    val list = mutableListOf<LocalSong>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.ALBUM_ID
    )
    // RELATIVE_PATH hanya ada sejak Android 10; DATA hanya dipakai di bawah itu.
    // Memasukkan kolom yang tidak ada di versi Android lama bisa menggagalkan query.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        projection += MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        @Suppress("DEPRECATION")
        projection += MediaStore.Audio.Media.DATA
    }
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    try {
        context.contentResolver.query(uri, projection.toTypedArray(), selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val relCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            @Suppress("DEPRECATION")
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val artwork = if (albumId > 0) {
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                } else null
                val relPath = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                val dataPath = if (dataCol >= 0) {
                    try { cursor.getString(dataCol).orEmpty() } catch (e: Exception) { "" }
                } else ""
                val folder = when {
                    dataPath.isNotBlank() ->
                        dataPath.substringBeforeLast("/", "").substringAfterLast("/", "Lainnya").ifBlank { "Lainnya" }
                    relPath.isNotBlank() ->
                        relPath.trimEnd('/').substringAfterLast("/", "Lainnya").ifBlank { "Lainnya" }
                    else -> "Lainnya"
                }
                list.add(
                    LocalSong(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown title",
                        artist = cursor.getString(artistCol) ?: "Unknown artist",
                        album = cursor.getString(albumCol).orEmpty(),
                        durationMs = cursor.getLong(durationCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        folder = folder,
                        uri = ContentUris.withAppendedId(uri, id),
                        artworkUri = artwork
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Query gagal -> kembalikan lagu yang sempat terbaca (bisa kosong).
    }
    list
}

// Cover album per lagu. Di Android 10+, URI albumart lama sering mengembalikan
// gambar default yang sama untuk semua lagu, jadi cover dibaca langsung dari
// file-nya lewat loadThumbnail (cover asli yang tertanam di file, atau null
// kalau tidak ada -> tampilkan placeholder ♪, bukan gambar default palsu).
// Di Android lama, pakai URI albumart seperti dulu.
@Composable
fun LocalArtThumb(song: LocalSong, sizeDp: Int) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var bitmap by remember(song.uri) { mutableStateOf<Bitmap?>(null) }
        var attempted by remember(song.uri) { mutableStateOf(false) }
        LaunchedEffect(song.uri) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(song.uri, Size(256, 256), null)
                } catch (e: Exception) {
                    null
                }
            }
            attempted = true
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF253129)),
                contentAlignment = Alignment.Center
            ) {
                if (attempted) Text("♪", color = Color(0xFF9AA99D), fontSize = 20.sp)
            }
        }
    } else {
        if (song.artworkUri != null) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                Modifier.size(sizeDp.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF253129)),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", color = Color(0xFF9AA99D), fontSize = 20.sp)
            }
        }
    }
}

// Indikator lagu sedang diputar: 4 batang equalizer beranimasi.
// (Dekoratif, bukan gelombang suara live dari audio.)
@Composable
private fun PlayingBars() {
    val transition = rememberInfiniteTransition(label = "playing-bars")
    val h1 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "b1")
    val h2 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "b2")
    val h3 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "b3")
    val h4 by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "b4")
    Row(
        Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3, h4).forEach { h ->
            Box(
                Modifier.width(3.dp).height((4 + h * 12).dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFF8AFF00))
            )
        }
    }
}

@Composable
private fun LocalSongRow(
    song: LocalSong,
    onPlay: () -> Unit,
    onToggleFavorite: (LocalSong) -> Unit,
    onAdd: (LocalSong) -> Unit,
    isCurrent: Boolean = false,
    showBars: Boolean = false
) {
    val track = song.toOnlineTrack()
    val fav = PlaylistStore.isFavorite(track)
    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocalArtThumb(song, 56)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Color(0xFF8AFF00) else Color(0xFFF1F5F1),
                fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    color = Color(0xFF9AA99D), fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${song.durationMs / 60000}:${((song.durationMs / 1000) % 60).toString().padStart(2, '0')}",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1
                )
            }
        }
        if (showBars) {
            PlayingBars()
            Spacer(Modifier.width(10.dp))
        }
        var menuExpanded by remember(song) { mutableStateOf(false) }
        Box {
            TextButton(onClick = { menuExpanded = true }) {
                Text("⋮", color = Color(0xFF8AFF00), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (fav) "Hapus dari Favorit" else "Tambah ke Favorit") },
                    onClick = { menuExpanded = false; onToggleFavorite(song) }
                )
                DropdownMenuItem(
                    text = { Text("Tambah ke Playlist") },
                    onClick = { menuExpanded = false; onAdd(song) }
                )
            }
        }
    }
    HorizontalDivider(color = Color(0xFF253129))
}

@Composable
private fun LibTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF8AFF00) else Color(0xFF9AA99D),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.height(3.dp).width(28.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color(0xFF8AFF00) else Color.Transparent)
        )
    }
}

@Composable
fun LibraryScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayLocal: (List<LocalSong>, Int) -> Unit,
    nowTitle: String = "",
    nowArtist: String = "",
    isPlaying: Boolean = false
) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(LibSort.Title) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(LibTab.All) }
    var openGroup by remember { mutableStateOf<Pair<LibTab, String>?>(null) }
    var addTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            songs = loadLocalSongs(context)
            loaded = true
        }
    }

    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }
    val displayed = remember(filtered, sortBy) {
        when (sortBy) {
            LibSort.Title -> filtered.sortedBy { it.title.lowercase() }
            LibSort.Artist -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            LibSort.Album -> filtered.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
            LibSort.Recent -> filtered.sortedByDescending { it.dateAddedSec }
        }
    }

    fun toggleFavorite(song: LocalSong) {
        val track = song.toOnlineTrack()
        val added = PlaylistStore.toggleFavorite(track)
        status = if (added) "Ditambahkan ke Favorit: ${song.title}" else "Dihapus dari Favorit: ${song.title}"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Lokal", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            if (!hasPermission) "Izinkan akses musik untuk melihat lagu di HP."
            else if (!loaded) "Memuat lagu..."
            else if (songs.isEmpty()) "Belum ada lagu di HP."
            else if (query.isBlank()) "${songs.size} lagu di HP"
            else "${displayed.size} dari ${songs.size} lagu",
            color = Color(0xFF9AA99D), fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))

        if (!hasPermission) {
            Button(onClick = onRequestPermission) { Text("Izinkan Akses Musik") }
        } else {
            // Bar pencarian gaya referensi: pil membulat + tombol hijau,
            // ikon urutkan, dan tombol putar semua.
            val focusManager = LocalFocusManager.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; openGroup = null },
                    placeholder = { Text("Cari lagu...") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    trailingIcon = {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF8AFF00))
                                .clickable { focusManager.clearFocus() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⌕", color = Color(0xFF050805), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text("⇅", color = Color(0xFF8AFF00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Judul (A-Z)") },
                            onClick = { sortBy = LibSort.Title; sortMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Artis (A-Z)") },
                            onClick = { sortBy = LibSort.Artist; sortMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Album (A-Z)") },
                            onClick = { sortBy = LibSort.Album; sortMenuOpen = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Terbaru ditambahkan") },
                            onClick = { sortBy = LibSort.Recent; sortMenuOpen = false }
                        )
                    }
                }
                TextButton(
                    onClick = { if (displayed.isNotEmpty()) onPlayLocal(displayed, 0) },
                    enabled = displayed.isNotEmpty()
                ) {
                    Text("▶", color = Color(0xFF8AFF00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                LibTabItem("Semua", tab == LibTab.All) { tab = LibTab.All; openGroup = null }
                LibTabItem("Artis", tab == LibTab.Artists) { tab = LibTab.Artists; openGroup = null }
                LibTabItem("Album", tab == LibTab.Albums) { tab = LibTab.Albums; openGroup = null }
                LibTabItem("Folder", tab == LibTab.Folders) { tab = LibTab.Folders; openGroup = null }
            }
            if (status.isNotBlank()) {
                Text(status, color = Color(0xFF9AA99D), fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
            }

            val detail = openGroup
            if (detail != null) {
                val detailSongs = when (detail.first) {
                    LibTab.Artists -> displayed.filter { it.artist.ifBlank { "Unknown artist" } == detail.second }
                    LibTab.Albums -> displayed.filter { it.album.ifBlank { "Unknown album" } == detail.second }
                    LibTab.Folders -> displayed.filter { it.folder == detail.second }
                    LibTab.All -> emptyList()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openGroup = null }) {
                        Text("‹ KEMBALI", color = Color(0xFF8AFF00), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(detail.second, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${detailSongs.size} lagu", color = Color(0xFF9AA99D), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (detailSongs.isEmpty()) {
                    Text("Tidak ada lagu di sini.", color = Color(0xFF9AA99D), fontSize = 13.sp)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(detailSongs) { index, song ->
                            val current = nowTitle.isNotBlank() && song.title == nowTitle && song.artist == nowArtist
                            LocalSongRow(
                                song = song,
                                onPlay = { onPlayLocal(detailSongs, index) },
                                onToggleFavorite = { toggleFavorite(it) },
                                onAdd = { addTarget = it.toOnlineTrack() },
                                isCurrent = current,
                                showBars = current && isPlaying
                            )
                        }
                    }
                }
            } else if (tab == LibTab.All) {
                if (displayed.isEmpty()) {
                    Text(
                        if (songs.isEmpty()) "Belum ada lagu di HP." else "Tidak ada hasil untuk pencarian.",
                        color = Color(0xFF9AA99D), fontSize = 13.sp
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(displayed) { index, song ->
                            val current = nowTitle.isNotBlank() && song.title == nowTitle && song.artist == nowArtist
                            LocalSongRow(
                                song = song,
                                onPlay = { onPlayLocal(displayed, index) },
                                onToggleFavorite = { toggleFavorite(it) },
                                onAdd = { addTarget = it.toOnlineTrack() },
                                isCurrent = current,
                                showBars = current && isPlaying
                            )
                        }
                    }
                }
            } else {
                val groups = remember(displayed, tab) {
                    when (tab) {
                        LibTab.Artists -> displayed.groupBy { it.artist.ifBlank { "Unknown artist" } }
                            .toList().sortedBy { it.first.lowercase() }
                        LibTab.Albums -> displayed.groupBy { it.album.ifBlank { "Unknown album" } }
                            .toList().sortedBy { it.first.lowercase() }
                        LibTab.Folders -> displayed.groupBy { it.folder }
                            .toList().sortedBy { it.first.lowercase() }
                        LibTab.All -> emptyList()
                    }
                }
                if (groups.isEmpty()) {
                    Text("Tidak ada grup di sini.", color = Color(0xFF9AA99D), fontSize = 13.sp)
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(groups) { (name, groupSongs) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { openGroup = tab to name }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LocalArtThumb(groupSongs.first(), 46)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = Color(0xFFF1F5F1), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(
                                        if (tab == LibTab.Albums) "${groupSongs.first().artist}  ·  ${groupSongs.size} lagu"
                                        else "${groupSongs.size} lagu",
                                        color = Color(0xFF9AA99D), fontSize = 11.sp, maxLines = 1
                                    )
                                }
                                Text("›", color = Color(0xFF9AA99D), fontSize = 20.sp)
                            }
                            HorizontalDivider(color = Color(0xFF253129))
                        }
                    }
                }
            }
        }

        // Dialog tambah ke playlist (lagu lokal yang sudah diubah jadi OnlineTrack).
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
                        var newPlaylistName by remember { mutableStateOf("") }
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
