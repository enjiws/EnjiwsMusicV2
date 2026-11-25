package com.enjiws.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val NeonGreen = Color(0xFF39FF14)
val LangsatYellow = Color(0xFFF3E5AB)
val DarkGray = Color(0xFF121212)
val SurfaceCard = Color(0xFF1E1E1E)

val EnjiwsColorScheme = darkColorScheme(
    background = AmoledBlack,
    surface = SurfaceCard,
    primary = NeonGreen,
    secondary = LangsatYellow,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = AmoledBlack,
    onSecondary = AmoledBlack
)

@Composable
fun EnjiwsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EnjiwsColorScheme,
        content = content
    )
}
