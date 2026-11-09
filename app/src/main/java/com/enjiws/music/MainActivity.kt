package com.enjiws.music

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.enjiws.music.data.AppScope
import com.enjiws.music.data.LocalLibrary
import com.enjiws.music.data.Store
import com.enjiws.music.playback.BitPerfect
import com.enjiws.music.data.Prefs
import com.enjiws.music.data.UiBus
import com.enjiws.music.playback.PlayerHub
import com.enjiws.music.ui.EnjiwsTheme
import com.enjiws.music.ui.MainApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        LocalLibrary.permitted = granted
        if (granted) LocalLibrary.refresh(this)
    }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LocalLibrary.requestPermission = { audioLauncher.launch(audioPermission) }
        LocalLibrary.permitted = hasAudioPermission()
        PlayerHub.connect(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            EnjiwsTheme {
                MainApp(
                    onExport = { exportLauncher.launch("enjiws-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalLibrary.permitted = hasAudioPermission()
        LocalLibrary.refresh(this)
        if (Prefs.bitPerfect) BitPerfect.apply(this, true)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    private fun writeBackup(uri: Uri) {
        AppScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) } != null
                }.getOrDefault(false)
            }
            UiBus.toast(if (ok) "Cadangan disimpan" else "Gagal menyimpan cadangan")
        }
    }

    private fun readBackup(uri: Uri) {
        AppScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
            }
            val added = text?.let { runCatching { Store.importJson(it) }.getOrNull() }
            UiBus.toast(if (added == null) "File cadangan tidak valid" else "Dipulihkan: $added item baru")
        }
    }
}
