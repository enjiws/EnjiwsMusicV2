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

/** Garis tepi tipis dan tenang untuk kartu/tombol (tanpa gradien warna-warni). */
val GlassBorder: Brush
    get() = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f)))

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

/** Latar polos hitam dengan semburat aksen sangat tipis di bagian atas. */
fun Modifier.aurora(): Modifier = drawBehind {
    val w = size.width
    drawRect(Color(0xFF08080A))
    val accent = currentAccent()
    val c = Offset(w * 0.5f, 0f)
    drawCircle(
        brush = Brush.radialGradient(listOf(accent.color.copy(alpha = 0.10f), Color.Transparent), center = c, radius = w * 0.9f),
        radius = w * 0.9f, center = c
    )
}

/** Kartu datar: permukaan solid dengan garis tepi tipis. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

/** Tombol ikon bulat bergaya kaca. */
@Composable
fun GlassIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(onClick).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** Tombol bulat solid warna aksen. */
@Composable
fun GradientCircleButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).pressable(onClick)
            .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Warna kolom teks yang seragam dengan tema kaca. */
@Composable
fun premiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary
)
