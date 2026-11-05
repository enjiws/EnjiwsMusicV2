package com.enjiws.music.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import com.enjiws.music.data.Subsonic
import kotlinx.coroutines.launch
import androidx.compose.material3.Switch
import com.enjiws.music.playback.AddonHub
import com.enjiws.music.playback.AudioAddon
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.Source
import com.enjiws.music.data.Store
import com.enjiws.music.data.UiBus

@Composable
fun SettingsScreen(onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var eq by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            Text("Pengaturan", style = MaterialTheme.typography.titleLarge)
        }

        Header("Tampilan")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sistem", "Gelap", "Terang").forEachIndexed { i, label ->
                FilterChip(selected = Prefs.themeMode == i, onClick = { Prefs.updateThemeMode(i) }, label = { Text(label) })
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SwitchRow("Warna dinamis", "Ikuti warna wallpaper (Android 12+)", Prefs.dynamicColor) { Prefs.updateDynamicColor(it) }
        }

        Header("Pemutaran")
        SwitchRow("Radio otomatis", "Tambahkan lagu terkait saat antrean habis", Prefs.autoplay) { Prefs.updateAutoplay(it) }
        SwitchRow("Hemat data", "Pilih kualitas audio online yang lebih rendah", Prefs.dataSaver) { Prefs.updateDataSaver(it) }
        ActionRow("Equalizer", "Atur frekuensi suara") { eq = true }

        Header("Addon audio")
        AddonHub.all.forEach { AddonCard(it) }
        Text(
            "Efek aktif saat lagu diputar. Tidak semua perangkat mendukung semua efek.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Header("Mood AI (opsional)")
        OutlinedTextField(
            value = Prefs.apiKey, onValueChange = { Prefs.updateApiKey(it) }, singleLine = true,
            label = { Text("API key Claude") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Text(
            "Tanpa key, mood dideteksi dengan kata kunci lokal. Dengan key, teks perasaanmu dikirim ke Claude (Anthropic) " +
                "dan pemakaiannya ditagih ke akunmu. Key tersimpan di HP ini saja; jangan dibagikan.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Header("Server musik pribadi (Subsonic / Navidrome)")
        OutlinedTextField(
            value = Prefs.serverUrl, onValueChange = { Prefs.updateServerUrl(it) }, singleLine = true,
            label = { Text("Alamat server, mis. http://192.168.1.5:4533") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        OutlinedTextField(
            value = Prefs.serverUser, onValueChange = { Prefs.updateServerUser(it) }, singleLine = true,
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        OutlinedTextField(
            value = Prefs.serverPass, onValueChange = { Prefs.updateServerPass(it) }, singleLine = true,
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { serverStatus = "Menguji…"; scope.launch { serverStatus = Subsonic.test() } }) { Text("Tes koneksi") }
        }
        if (serverStatus.isNotBlank()) Text(serverStatus, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        Text(
            "Lagu dari servermu ikut muncul di pencarian dan diputar apa adanya (FLAC/Hi-Res tanpa transcode). " +
                "Password tersimpan di HP ini saja.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Header("Data")
        ActionRow("Cadangkan favorit & playlist", "Simpan ke file JSON", onExport)
        ActionRow("Pulihkan dari cadangan", "Gabungkan dari file JSON", onImport)
        ActionRow("Hapus riwayat putar", "${Store.history.size} lagu tercatat") { Store.clearHistory(); UiBus.toast("Riwayat dihapus") }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("ENJIWS Music 2.0", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("", Modifier.padding(bottom = 24.dp))
    }
    if (eq) EqualizerSheet { eq = false }
}

@Composable
fun AddonCard(a: AudioAddon) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.title, style = MaterialTheme.typography.bodyLarge)
                Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = a.enabled, onCheckedChange = { a.updateEnabled(it) })
        }
        if (a.enabled) {
            Slider(
                value = a.level.toFloat(), onValueChange = { a.updateLevel(it.toInt()) },
                onValueChangeFinished = { a.commitLevel() }, valueRange = 0f..a.maxLevel.toFloat()
            )
            Text(a.levelLabel(a.level), style = MaterialTheme.typography.bodySmall)
        }
        if (!a.supported) {
            Text("Tidak didukung di perangkat ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
