package com.enjiws.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.Prefs

/** Aksen yang sedang dipilih di Pengaturan. */
fun currentAccent(): Accent = Accents[Prefs.accent.coerceIn(Accents.indices)]

/** Pantulan tepi kaca: terang di sudut kiri-atas, memudar, lalu sentuhan warna aksen. */
val GlassBorder: Brush
    get() = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f), currentAccent().color.copy(alpha = 0.24f)))

/** Animasi tekan: mengecil sedikit saat disentuh lalu memantul kembali. */
fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Latar "aurora": cahaya aksen dari kiri-atas dan semburat glow di kanan, di atas hitam pekat. */
fun Modifier.aurora(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF050807))
    val accent = currentAccent()
    val c1 = Offset(w * 0.05f, h * 0.02f)
    drawCircle(
        brush = Brush.radialGradient(listOf(accent.color.copy(alpha = 0.24f), Color.Transparent), center = c1, radius = w),
        radius = w, center = c1
    )
    val c2 = Offset(w, h * 0.38f)
    drawCircle(
        brush = Brush.radialGradient(listOf(accent.glow.copy(alpha = 0.10f), Color.Transparent), center = c2, radius = w * 0.8f),
        radius = w * 0.8f, center = c2
    )
}

/** Kartu kaca: isi tembus pandang dengan tepi bergradien. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, GlassBorder),
        content = content
    )
}

/** Tombol ikon bulat bergaya kaca. */
@Composable
fun GlassIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(onClick).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f)).border(1.dp, GlassBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** Tombol bulat bergradien lime-kuning dengan cahaya di bawahnya. */
@Composable
fun GradientCircleButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).pressable(onClick)
            .shadow(14.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            .clip(CircleShape).background(AccentGradient),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Warna kolom teks yang seragam dengan tema kaca. */
@Composable
fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.60f),
    cursorColor = MaterialTheme.colorScheme.primary
)
