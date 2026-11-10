package com.enjiws.music.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Activity
import com.enjiws.music.R
import com.enjiws.music.data.Prefs

val Lime = Color(0xFF8AFF00)
val LimeYellow = Color(0xFFFFEA00)

/** Emerald khas "Dark Emerald Luxury": untuk glow dan aksen gelap. */
val Emerald = Color(0xFF00C896)
val EmeraldDeep = Color(0xFF0E3B2E)

/** Gradien aksen khas EW (lime → kuning) untuk tombol putar utama. */
val AccentGradient = Brush.linearGradient(listOf(Lime, LimeYellow))

/** Font editorial aplikasi: Poppins (Regular–ExtraBold). */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
)

/** Emas khas lencana Hi-Res. */
val HiResGold = Color(0xFFFFD36B)

private val Dark = darkColorScheme(
    primary = Lime, onPrimary = Color(0xFF0B1500),
    primaryContainer = Color(0xFF263D00), onPrimaryContainer = Color(0xFFB9F55A),
    secondary = Color(0xFFB6CCA0), onSecondary = Color(0xFF1F3010),
    secondaryContainer = Color(0xFF2E4220), onSecondaryContainer = Color(0xFFD1E8BA),
    tertiary = Color(0xFFFFEA00), onTertiary = Color(0xFF211C00),
    background = Color(0xFF050807), onBackground = Color(0xFFE4E9DE),
    surface = Color(0xFF050807), onSurface = Color(0xFFE4E9DE),
    surfaceVariant = Color(0xFF223129), onSurfaceVariant = Color(0xFFB3BDAA),
    surfaceContainerLowest = Color(0xFF030604), surfaceContainerLow = Color(0xFF0A100C),
    surfaceContainer = Color(0xFF101710), surfaceContainerHigh = Color(0xFF16211A),
    surfaceContainerHighest = Color(0xFF1E2C22),
    outline = Color(0xFF7D8776), outlineVariant = Color(0xFF2C3A30),
    error = Color(0xFFFF6B6B)
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F6B00), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F26A), onPrimaryContainer = Color(0xFF0A2000),
    secondary = Color(0xFF55624A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E7C9), onSecondaryContainer = Color(0xFF131F0B),
    tertiary = Color(0xFF6B5E00), onTertiary = Color.White,
    background = Color(0xFFF8FBF1), onBackground = Color(0xFF191D16),
    surface = Color(0xFFF8FBF1), onSurface = Color(0xFF191D16),
    surfaceVariant = Color(0xFFDFE5D4), onSurfaceVariant = Color(0xFF43493E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F6EA),
    surfaceContainer = Color(0xFFECF0E4), surfaceContainerHigh = Color(0xFFE6EADE),
    surfaceContainerHighest = Color(0xFFE0E4D8),
    outline = Color(0xFF73796B), outlineVariant = Color(0xFFC3C9B8)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Poppins, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Poppins, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun EnjiwsTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.themeMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val scheme = when {
        Prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
