package com.enjiws.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.enjiws.music.ui.theme.*

data class Song(val title: String, val artist: String, val duration: String)

@Composable
fun LibraryScreen() {
    var searchQuery by remember { mutableStateOf("") }
    
    val mockSongs = listOf(
        Song("Neon Dreams", "Enjiws Lab", "3:42"),
        Song("Dark romance Theme", "dingekk", "4:15"),
        Song("Termux Symphony No. 9", "Kotlin Dev", "2:58"),
        Song("Langsat Sunset", "Resso Beats", "3:10"),
        Song("ChainBreaker OST", "Minecraft Vibe", "5:02")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Koleksi Musik",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari lagu lokal...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonGreen) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mockSongs) { song ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonGreen)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(song.artist, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }

                        Text(song.duration, color = LangsatYellow, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
