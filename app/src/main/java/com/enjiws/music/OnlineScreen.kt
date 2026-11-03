package com.enjiws.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.enjiws.music.ui.theme.*

@Composable
fun OnlineScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Stream,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Streaming & Downloader Module",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Terhubung dengan Engine Piped & NewPipe untuk pencarian lagu online tanpa iklan.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = AmoledBlack),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cari & Download Lagu")
        }
    }
}
