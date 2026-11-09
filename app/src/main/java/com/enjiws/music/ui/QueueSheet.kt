package com.enjiws.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.Store
import com.enjiws.music.data.UiBus
import com.enjiws.music.playback.PlayerHub

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val s by PlayerHub.state.collectAsState()
    val state = rememberLazyListState()
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (s.index > 0) state.scrollToItem(s.index) }
    val rowPx = with(LocalDensity.current) { 56.dp.toPx() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Antrean (${s.queue.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { saving = true }, enabled = s.queue.isNotEmpty()) { Text("Simpan") }
                TextButton(onClick = { PlayerHub.clearUpcoming() }, enabled = s.index in 0 until s.queue.lastIndex) { Text("Kosongkan") }
            }
            if (s.queue.isEmpty()) {
                Text("Antrean kosong.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(s.queue, key = { i, t -> "$i${t.id}" }) { i, t ->
                    val current = i == s.index
                    var drag by remember { mutableFloatStateOf(0f) }
                    Row(
                        Modifier.fillMaxWidth().height(56.dp)
                            .zIndex(if (drag != 0f) 1f else 0f)
                            .graphicsLayer { translationY = drag }
                            .clickable { PlayerHub.skipTo(i) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Cover(t.cover, 44.dp, radius = 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current) PlayingBars(active = s.isPlaying)
                        else IconButton(onClick = { PlayerHub.removeAt(i) }) {
                            Icon(Icons.Rounded.Close, "Hapus dari antrean", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Pegangan: tarik ke atas/bawah untuk memindahkan lagu.
                        Icon(
                            Icons.Rounded.DragHandle, "Geser untuk memindahkan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp).pointerInput(i) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val to = (i + (drag / rowPx).roundToInt()).coerceIn(0, s.queue.lastIndex)
                                        drag = 0f
                                        if (to != i) PlayerHub.move(i, to)
                                    },
                                    onDragCancel = { drag = 0f },
                                    onVerticalDrag = { change, amount -> change.consume(); drag += amount }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
    if (saving) NameDialog("Simpan antrean sebagai playlist", initial = "Antrean", confirm = "Simpan", onDismiss = { saving = false }) {
        Store.createPlaylist(it, s.queue)
        UiBus.toast("Playlist “$it” dibuat")
        saving = false
    }
}
