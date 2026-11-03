package com.enjiws.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enjiws.music.ui.theme.*

@Composable
fun NowPlayingScreen(
    trackName: String,
    artist: String,
    isPlaying: Boolean
) {
    var progress by remember { mutableFloatStateOf(0.35f) }
    var isFavorite by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceCard,
                            DarkGray,
                            AmoledBlack
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NeonGreen.copy(alpha = 0.3f), LangsatYellow.copy(alpha = 0.2f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EW",
                    fontSize = 64.sp,
                    color = NeonGreen,
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = trackName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) LangsatYellow else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkGray)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪ Resonansi malam dalam irama synthesizer... ♪",
                    color = LangsatYellow,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Slider(
                value = progress,
                onValueChange = { progress = it },
                colors = SliderDefaults.colors(
                    thumbColor = NeonGreen,
                    activeTrackColor = NeonGreen,
                    inactiveTrackColor = DarkGray
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1:24", color = Color.Gray, fontSize = 12.sp)
                Text("3:45", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = LangsatYellow
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = AmoledBlack,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = LangsatYellow
                    )
                }
            }
        }
    }
}
