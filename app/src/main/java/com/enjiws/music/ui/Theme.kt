package com.enjiws.music.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.enjiws.music.R
import com.enjiws.music.data.Prefs

// ---------------------------------------------------------------- warna dasar
// Satu palet netral (hitam → abu) + SATU warna aksen. Teks selalu putih-hangat
// di atas gelap, jadi kontrasnya tinggi dan tidak ada lagi "hitam di atas hitam".

val Lime = Color(0xFF8AFF00)
val LimeYellow = Color(0xFFFFEA00)
val Emerald = Color(0xFF00C896)
val EmeraldDeep = Color(0xFF0E3B2E)

/** Emas khas lencana Hi-Res. */
val HiResGold = Color(0xFFFFD36B)

/** Teks utama & sekunder di atas permukaan gelap (kontras ≥ 12:1 dan ≥ 9:1 di atas hitam). */
val Ink = Color(0xFFF5F5F7)
val InkSoft = Color(0xFFB8B8C0)

/** Pilihan warna aksen (dipilih di Pengaturan). */
data class Accent(val name: String, val color: Color, val onColor: Color, val container: Color, val glow: Color)
val Accents = listOf(
    Accent("Lime", Lime, Color(0xFF0B1500), Color(0xFF263D00), LimeYellow),
    Accent("Emerald", Emerald, Color(0xFF00211A), Color(0xFF0E3B2E), Color(0xFF7CFFB2)),
    Accent("Emas", Color(0xFFFFC400), Color(0xFF241A00), Color(0xFF4A3600), Color(0xFFFFE082)),
    Accent("Pink", Color(0xFFFF4D8D), Color(0xFF2B0014), Color(0xFF571033), Color(0xFFFF8FBF)),
    Accent("Langit", Color(0xFF38BDF8), Color(0xFF001E2E), Color(0xFF0B3549), Color(0xFFA5E3FF))
)

/**
 * Aksen aktif untuk tombol utama. Sengaja SOLID (bukan gradien) supaya tampilan
 * lebih tenang dan premium. Nama fungsi dipertahankan agar file lain tidak perlu diubah.
 */
fun accentGradient(): Brush {
    val a = Accents[Prefs.accent.coerceIn(Accents.indices)]
    return Brush.linearGradient(listOf(a.color, a.color))
}

/** Alias lama (dipakai file Premium.kt): aksen aktif. */
val AccentGradient: Brush get() = accentGradient()

/** Font aplikasi: Poppins (Regular–ExtraBold). */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold)
)

// ---------------------------------------------------------------- skema warna

private fun darkSchemeFor(accent: Accent): ColorScheme = darkColorScheme(
    primary = accent.color, onPrimary = accent.onColor,
    primaryContainer = accent.container, onPrimaryContainer = accent.color,
    secondary = InkSoft, onSecondary = Color(0xFF111113),
    secondaryContainer = Color(0xFF2A2A2F), onSecondaryContainer = Ink,
    tertiary = HiResGold, onTertiary = Color(0xFF302300),
    background = Color(0xFF08080A), onBackground = Ink,
    surface = Color(0xFF08080A), onSurface = Ink,
    surfaceVariant = Color(0xFF1C1C1F), onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F11),
    surfaceContainer = Color(0xFF151517),
    surfaceContainerHigh = Color(0xFF1C1C1F),
    surfaceContainerHighest = Color(0xFF252529),
    outline = Color(0xFF8A8A94), outlineVariant = Color(0xFF2C2C31),
    error = Color(0xFFFF7A7A)
)

private val Light = lightColorScheme(
    primary = Color(0xFF0B6B52), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE3), onPrimaryContainer = Color(0xFF00382B),
    secondary = Color(0xFF4A4A52), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6EA), onSecondaryContainer = Color(0xFF111113),
    tertiary = Color(0xFF6B5E00), onTertiary = Color.White,
    background = Color(0xFFF7F7F8), onBackground = Color(0xFF111113),
    surface = Color(0xFFF7F7F8), onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE6E6EA), onSurfaceVariant = Color(0xFF4A4A52),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F1F3),
    surfaceContainer = Color(0xFFECECEF), surfaceContainerHigh = Color(0xFFE6E6EA),
    surfaceContainerHighest = Color(0xFFE0E0E5),
    outline = Color(0xFF6E6E78), outlineVariant = Color(0xFFD0D0D6)
)

// ---------------------------------------------------------------- tipografi
// Lebih besar & lebih jelas: teks kecil minimal 12sp, tidak ada ExtraBold berlebihan.

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Poppins, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = Poppins, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Poppins, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// Radius lebih konsisten: kecil untuk kontrol, sedang untuk kartu, besar untuk sheet.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EnjiwsTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.themeMode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val accent = Accents[Prefs.accent.coerceIn(Accents.indices)]
    val scheme = when {
        Prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkSchemeFor(accent)
        else -> Light.copy(
            primary = lerp(accent.color, Color.Black, 0.45f),
            primaryContainer = lerp(accent.color, Color.White, 0.75f),
            onPrimaryContainer = lerp(accent.color, Color.Black, 0.60f)
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
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes) {
        // Warna teks bawaan ikut skema, jadi teks tanpa warna eksplisit selalu terbaca.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}

/**
 * Paksa isi layar memakai skema GELAP, apa pun mode tema aplikasi.
 * Dipakai untuk layar yang latarnya selalu gelap (pemutar penuh) supaya teks
 * tanpa warna eksplisit tidak jadi hitam di atas hitam saat mode Terang.
 */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    val accent = Accents[Prefs.accent.coerceIn(Accents.indices)]
    MaterialTheme(
        colorScheme = darkSchemeFor(accent),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        CompositionLocalProvider(LocalContentColor provides Ink, content = content)
    }
}
