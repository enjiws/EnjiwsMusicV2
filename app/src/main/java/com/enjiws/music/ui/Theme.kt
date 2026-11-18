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
import androidx.compose.ui.graphics.lerp
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

/** Pilihan warna aksen (dipilih di Pengaturan). */
data class Accent(val name: String, val color: Color, val onColor: Color, val container: Color, val glow: Color)
val Accents = listOf(
    Accent("Lime", Lime, Color(0xFF0B1500), Color(0xFF263D00), LimeYellow),
    Accent("Emerald", Emerald, Color(0xFF00211A), Color(0xFF0E3B2E), Color(0xFF7CFFB2)),
    Accent("Emas", Color(0xFFFFC400), Color(0xFF241A00), Color(0xFF4A3600), Color(0xFFFFE082)),
    Accent("Pink", Color(0xFFFF4D8D), Color(0xFF2B0014), Color(0xFF571033), Color(0xFFFF8FBF)),
    Accent("Langit", Color(0xFF38BDF8), Color(0xFF001E2E), Color(0xFF0B3549), Color(0xFFA5E3FF))
)

/** Gradien aksen aktif (aksen → glow) untuk tombol putar utama. */
fun accentGradient(): Brush {
    val a = Accents[Prefs.accent.coerceIn(Accents.indices)]
    return Brush.linearGradient(listOf(a.color, a.glow))
}

/** Alias lama (dipakai file Premium.kt): gradien aksen aktif. */
val AccentGradient: Brush get() = accentGradient()

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
    primary = Emerald, onPrimary = Color(0xFF00251B),
    primaryContainer = Color(0xFF07513F), onPrimaryContainer = Color(0xFF9AF5D8),
    secondary = Color(0xFFC7D8CE), onSecondary = Color(0xFF20352D),
    secondaryContainer = Color(0xFF29483C), onSecondaryContainer = Color(0xFFDCEFE5),
    tertiary = HiResGold, onTertiary = Color(0xFF302300),
    background = Color(0xFF050A08), onBackground = Color(0xFFE8F0EB),
    surface = Color(0xFF050A08), onSurface = Color(0xFFE8F0EB),
    surfaceVariant = Color(0xFF20352C), onSurfaceVariant = Color(0xFFB5C9BE),
    surfaceContainerLowest = Color(0xFF030705),
    surfaceContainerLow = Color(0xFF0A120E),
    surfaceContainer = Color(0xFF101B15),
    surfaceContainerHigh = Color(0xFF17251D),
    surfaceContainerHighest = Color(0xFF203128),
    outline = Color(0xFF82998C), outlineVariant = Color(0xFF2A4035),
    error = Color(0xFFFF7777)
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
    val accent = Accents[Prefs.accent.coerceIn(Accents.indices)]
    val scheme = when {
        Prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark.copy(
            primary = accent.color, onPrimary = accent.onColor,
            primaryContainer = accent.container, onPrimaryContainer = accent.color
        )
        else -> Light.copy(
            primary = lerp(accent.color, Color.Black, 0.45f),
            primaryContainer = lerp(accent.color, Color.White, 0.75f),
            onPrimaryContainer = lerp(accent.color, Color.Black, 0.55f)
        )
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
