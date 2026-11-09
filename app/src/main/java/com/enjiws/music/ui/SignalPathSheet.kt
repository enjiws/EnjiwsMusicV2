package com.enjiws.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enjiws.music.data.AudioInfoRepo
import com.enjiws.music.data.StreamResolver
import com.enjiws.music.data.Track
import com.enjiws.music.playback.PathStep
import com.enjiws.music.playback.SignalPath

/** Jalur sinyal: sumber -> decoder -> pemrosesan -> mixer Android -> output. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalPathSheet(track: Track, speed: Float, onEffects: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = AudioInfoRepo.info[StreamResolver.refOf(track)]
    val steps = SignalPath.steps(context, track, info, speed)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Jalur sinyal", style = MaterialTheme.typography.titleMedium)
            if (info != null && info.tier.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        info.tier + " · " + info.detail, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, st -> StepRow(i + 1, st) }
            TextButton(onClick = onEffects) { Text("Buka efek & addon") }
        }
    }
}

@Composable
private fun StepRow(n: Int, st: PathStep) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(st.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Text(st.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (st.detail.isNotBlank()) Text(st.detail, style = MaterialTheme.typography.bodyMedium)
            if (st.note.isNotBlank()) Text(st.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
