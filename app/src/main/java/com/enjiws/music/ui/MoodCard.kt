package com.enjiws.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.Catalog
import com.enjiws.music.data.MoodAi
import com.enjiws.music.data.MoodResult
import com.enjiws.music.data.Source
import com.enjiws.music.data.Track
import com.enjiws.music.data.UiBus
import com.enjiws.music.playback.PlayerHub
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Ketik perasaan -> tebak mood -> cari & putar lagu yang cocok. */
@Composable
fun MoodCard() {
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MoodResult?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun submit() {
        if (text.isBlank() || loading) return
        focus.clearFocus()
        loading = true
        scope.launch {
            try {
                val r = MoodAi.analyze(text)
                result = r
                val lists = r.queries.take(3).map { q ->
                    async { runCatching { Catalog.searchSource(Source.YouTube, q) }.getOrDefault(emptyList()) }
                }.awaitAll()
                // Selang-seling antar kueri supaya variatif; buang mix/podcast yang terlalu panjang.
                val maxLen = lists.maxOfOrNull { it.size } ?: 0
                val mixed = buildList<Track> {
                    for (i in 0 until maxLen) lists.forEach { l -> l.getOrNull(i)?.let { add(it) } }
                }.distinctBy { it.key }.filter { it.canStream }
                val songs = mixed.filter { it.durationSeconds in 60L..600L }.ifEmpty { mixed }.take(20)
                if (songs.isEmpty()) UiBus.toast("Tidak menemukan lagu untuk mood ini") else PlayerHub.play(songs, 0, shuffle = false)
            } finally {
                loading = false
            }
        }
    }

    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(accentGradient()), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF0B1500), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Mood hari ini", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, maxLines = 3,
                placeholder = { Text("Lagi ngerasa apa? mis. capek abis kerja") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                trailingIcon = {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { submit() }, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim") }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            result?.let {
                Text("${it.emoji}  ${it.mood}", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (it.reply.isNotBlank()) Text(it.reply, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (it.viaAi) "Dianalisis AI" else "Deteksi lokal · tambahkan API key di Pengaturan untuk analisis AI",
                    Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
