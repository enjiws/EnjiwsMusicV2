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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.AppScope
import com.enjiws.music.data.Catalog
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Source
import com.enjiws.music.data.Store
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PlayerHub
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** State pencarian di level aplikasi: teks & hasil tidak hilang saat pindah tab atau membuka pemutar. */
object SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
    var message by mutableStateOf("")
    private var job: Job? = null

    fun run(q: String) {
        val text = q.trim()
        if (text.isBlank()) return
        query = text
        Store.addSearch(text)
        job?.cancel()
        loading = true
        message = ""
        job = AppScope.launch {
            try {
                val out = Catalog.search(text, Prefs.sources)
                results = out.tracks
                val counts = out.counts.entries.joinToString(" · ") { "${it.key.label} ${it.value}" }
                val failed = if (out.failed.isNotEmpty()) " · Gagal: " + out.failed.joinToString { it.label } else ""
                message = when {
                    out.tracks.isEmpty() && out.failed.size == Prefs.sources.size ->
                        "Semua sumber gagal diakses. Periksa koneksi internet lalu coba lagi."
                    out.tracks.isEmpty() -> "Tidak ada hasil untuk “$text”.$failed"
                    else -> "${out.tracks.size} hasil · $counts$failed"
                }
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SearchScreen() {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onFocusChanged { focused = it.isFocused }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Source.values().forEach { s ->
                FilterChip(
                    selected = s in Prefs.sources, onClick = { Prefs.toggleSource(s) },
                    label = { Text(s.label) }, modifier = Modifier.padding(horizontal = 0.dp)
                )
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
                SearchState.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SearchState.results.isNotEmpty() -> {
                    val playable = SearchState.results.filter { it.canStream }
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        item {
                            Text(SearchState.message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        itemsIndexed(SearchState.results, key = { i, t -> "$i${t.key}" }) { _, t ->
                            TrackRow(t, showSource = true, onClick = {
                                if (t.canStream) PlayerHub.play(playable, playable.indexOfFirst { it.key == t.key })
                                else if (t.storeUrl.isNotBlank()) runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.storeUrl)))
                                }
                            })
                        }
                    }
                }
                else -> EmptyState(
                    Icons.Rounded.Search,
                    if (SearchState.message.isNotBlank()) "Tidak ada hasil" else "Cari musik",
                    SearchState.message.ifBlank { "Ketik judul lagu atau nama artis. Pilih sumber lewat chip di atas." },
                    Modifier.align(Alignment.Center)
                )
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
