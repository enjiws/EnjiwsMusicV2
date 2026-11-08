package com.enjiws.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tema neon hijau-kuning senada icon EW: aksen lime terang di atas dasar
// hitam kehijauan.
@Composable
fun EnjiwsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AFF00),
            secondary = Color(0xFFFFEA00),
            background = Color(0xFF050805),
            surface = Color(0xFF0E150E)
        ),
        content = content
    )
}
