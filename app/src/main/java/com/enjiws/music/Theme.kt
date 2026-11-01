package com.enjiws.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun EnjiwsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF91D6A0),
            background = Color(0xFF0A0F0A),
            surface = Color(0xFF101510)
        ),
        content = content
    )
}
