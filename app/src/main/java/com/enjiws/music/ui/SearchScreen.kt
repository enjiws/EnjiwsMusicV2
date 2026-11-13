package com.enjiws.music.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.enjiws.music.data.BrowseItem
import com.enjiws.music.data.Browse
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.AppScope
import com.enjiws.music.data.Catalog
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Source
import com.enjiws.music.data.Store
import com.enjiws.music.data.Subsonic
import com.enjiws.music.data.Track
import com.enjiws.music.data.YtSearchPager
import com.enjiws.music.playback.PlayerHub
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** State pencarian di level aplikasi: teks & hasil tidak hilang saat pindah tab atau membuka pemutar. */
object SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
    var message by mutableStateOf("")
    private var job: Job? = null

    private var runId = 0
    /** 0 Semua, 1 Lagu, 2 Album, 3 Artis, 4 Playlist */
    var category by mutableIntStateOf(0)
    var albums by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var artists by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var playlists by mutableStateOf<List<BrowseItem>>(emptyList()); private set
    var loadingCat by mutableStateOf(false); private set

    fun selectCategory(c: Int) {
        category = c
        if (c == 4 && playlists.isEmpty() && query.isNotBlank()) {
            val q = query
            AppScope.launch {
                loadingCat = true
                try { playlists = runCatching { Browse.search("playlist", q) }.getOrDefault(emptyList()) } finally { loadingCat = false }
            }
        }
    }
    private var pager: YtSearchPager? = null
    var hasMore by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set

    /** Dipanggil saat pengguna mendekati ujung daftar: ambil halaman berikutnya dari YouTube. */
    fun loadMore() {
        val p = pager ?: return
        if (loadingMore || loading || !hasMore) return
        val id = runId
        loadingMore = true
        AppScope.launch {
            try {
                val more = runCatching { withTimeout(12_000L) { p.more() } }.getOrDefault(emptyList())
                if (id == runId) {
                    val known = results.map { it.key }.toSet()
                    results = results + more.filter { it.key !in known }
                    hasMore = p.hasMore
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun run(q: String) {
        val text = q.trim()
        if (text.isBlank()) return
        query = text
        Store.addSearch(text)
        job?.cancel()
        val id = ++runId
        loading = true
        message = "Mencari…"
        results = emptyList()
        hasMore = false
        loadingMore = false
        albums = emptyList()
        artists = emptyList()
        playlists = emptyList()
        val yt = YtSearchPager(text)
        pager = yt
        val sources = if (Subsonic.configured) setOf(Source.YouTube, Source.Server) else setOf(Source.YouTube)
        job = AppScope.launch {
            val collected = mutableListOf<Track>()
            val counts = mutableListOf<String>()
            val failed = mutableListOf<String>()
            // Album dan artis dicari bersamaan dengan lagu (katalog YouTube Music).
            launch { runCatching { Browse.search("album", text) }.onSuccess { if (id == runId) albums = it } }
            launch { runCatching { Browse.search("artist", text) }.onSuccess { if (id == runId) artists = it } }
            // Tiap sumber jalan sendiri; hasilnya langsung tampil begitu selesai.
            sources.map { s ->
                launch {
                    runCatching { withTimeout(12_000L) { if (s == Source.YouTube) yt.first() else Catalog.searchSource(s, text) } }
                        .onSuccess { list ->
                            collected += list
                            counts += "${s.label} ${list.size}"
                            results = Catalog.sortResults(collected.distinctBy { it.key }, text)
                            if (s == Source.YouTube) hasMore = yt.hasMore
                        }
                        .onFailure { failed += s.label }
                }
            }.forEach { it.join() }
            if (id != runId) return@launch
            val failedText = if (failed.isNotEmpty()) " · Gagal: " + failed.joinToString() else ""
            message = when {
                results.isEmpty() && failed.size == sources.size ->
                    "Semua sumber gagal diakses. Periksa koneksi internet lalu coba lagi."
                results.isEmpty() -> "Tidak ada hasil untuk “$text”.$failedText"
                else -> "${results.size} hasil · ${counts.joinToString(" · ")}$failedText"
            }
            loading = false
        }
    }
}

@Composable
fun SearchScreen(onOpenArtist: (BrowseItem) -> Unit, onOpenAlbum: (BrowseItem) -> Unit) {
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    var focused by remember { mutableStateOf(SearchState.results.isEmpty()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val query = SearchState.query

    LaunchedEffect(query, focused) {
        if (focused && query.trim().length >= 2) {
            delay(250)
            suggestions = Catalog.suggestions(query.trim())
        } else suggestions = emptyList()
    }

    fun submit(q: String) {
        focus.clearFocus()
        SearchState.run(q)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Cari", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { SearchState.query = it },
            singleLine = true,
            placeholder = { Text("Judul lagu, artis…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { SearchState.query = "" }) { Icon(Icons.Rounded.Clear, "Hapus") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).onFocusChanged { focused = it.isFocused }
        )
        val hasContent = SearchState.results.isNotEmpty() || SearchState.albums.isNotEmpty() ||
            SearchState.artists.isNotEmpty() || SearchState.loading
        if (hasContent && SearchState.query.isNotBlank()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Semua", "Lagu", "Album", "Artis", "Playlist").forEachIndexed { i, label ->
                    FilterChip(selected = SearchState.category == i, onClick = { SearchState.selectCategory(i) }, label = { Text(label) })
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                focused && suggestions.isNotEmpty() -> LazyColumn {
                    itemsIndexed(suggestions) { _, s ->
                        SuggestionRow(Icons.Rounded.Search, s, onClick = { submit(s) })
                    }
                }
                focused && query.isBlank() && Store.searchHistory.isNotEmpty() -> LazyColumn {
                    item {
                        SectionTitle("Pencarian terakhir") { TextButton(onClick = { Store.clearSearch() }) { Text("Hapus semua") } }
                    }
                    itemsIndexed(Store.searchHistory) { _, s ->
                        SuggestionRow(Icons.Rounded.History, s, onClick = { submit(s) }, onRemove = { Store.removeSearch(s) })
                    }
                }
                hasContent && SearchState.category >= 2 -> {
                    val shown = when (SearchState.category) { 2 -> SearchState.albums; 3 -> SearchState.artists; else -> SearchState.playlists }
                    if (shown.isEmpty()) {
                        if (SearchState.loading || SearchState.loadingCat) CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else Text("Tidak ada hasil di kategori ini.", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(shown, key = { it.url }) { b ->
                                BrowseRow(b) { if (b.kind == "artist") onOpenArtist(b) else onOpenAlbum(b) }
                            }
                        }
                    }
                }
                SearchState.loading && SearchState.results.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SearchState.results.isNotEmpty() -> {
                    val listState = rememberLazyListState()
                    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
                    LaunchedEffect(lastVisible, SearchState.results.size) {
                        if (SearchState.results.isNotEmpty() && lastVisible >= SearchState.results.size - 2) SearchState.loadMore()
                    }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            if (SearchState.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(SearchState.message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (SearchState.category == 0 && SearchState.artists.isNotEmpty()) {
                            item { SectionTitle("Artis") }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(SearchState.artists.take(8), key = { it.url }) { a -> ArtistCircle(a) { onOpenArtist(a) } }
                                }
                            }
                        }
                        if (SearchState.category == 0 && SearchState.albums.isNotEmpty()) {
                            item { SectionTitle("Album") }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(SearchState.albums.take(8), key = { it.url }) { a ->
                                        SquareCard(a.thumb.ifBlank { null }, a.name, a.subtitle, onClick = { onOpenAlbum(a) })
                                    }
                                }
                            }
                            item { SectionTitle("Lagu") }
                        }
                        itemsIndexed(SearchState.results, key = { i, t -> "$i${t.key}" }) { _, t ->
                            TrackRow(t, showSource = true, onClick = {
                                if (t.canStream) PlayerHub.play(listOf(t), 0)
                                else if (t.storeUrl.isNotBlank()) runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.storeUrl)))
                                }
                            })
                        }
                        if (SearchState.loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
                else -> Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState())) {
                    EmptyState(
                        Icons.Rounded.Search,
                        if (SearchState.message.isNotBlank()) "Tidak ada hasil" else "Cari musik",
                        SearchState.message.ifBlank { "Ketik judul lagu atau nama artis." },
                        Modifier
                    )

                    SectionTitle("Jelajahi genre")
                    GenreChips { SearchState.run(it) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, Modifier.weight(1f), maxLines = 1)
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Rounded.Clear, "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        else Spacer(Modifier.width(12.dp))
    }
}

private val genres = listOf("Pop", "Rock", "Hip-Hop", "R&B", "EDM", "Jazz", "Klasik", "Metal", "Indie", "K-Pop", "Dangdut", "Lo-fi", "Reggae", "Country")

private fun genreQuery(g: String): String = when (g) {
    "Dangdut" -> "lagu dangdut populer"
    "Klasik" -> "classical music"
    "Lo-fi" -> "lofi hip hop"
    "K-Pop" -> "kpop hits"
    else -> "$g music"
}

/** Genre jadi dua baris chip yang bisa digeser; ketuk = cari musik genre itu. */
@Composable
private fun GenreChips(onPick: (String) -> Unit) {
    listOf(genres.take(7), genres.drop(7)).forEach { row ->
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { g -> FilterChip(selected = false, onClick = { onPick(genreQuery(g)) }, label = { Text(g) }) }
        }
    }
}
