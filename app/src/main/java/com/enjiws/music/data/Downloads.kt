package com.enjiws.music.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Downloads {
    private fun sanitize(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "track" }.take(100)

    /** Resolve stream lalu serahkan ke DownloadManager (jalan di background dengan notifikasi sistem). */
    fun start(context: Context, track: Track) {
        val app = context.applicationContext
        if (track.isLocal) { UiBus.toast("Lagu ini sudah ada di perangkat"); return }
        if (track.isPreview) { UiBus.toast("Apple Music hanya menyediakan pratinjau 30 detik"); return }
        if (!track.canStream) { UiBus.toast("Lagu ini tidak bisa diunduh"); return }
        UiBus.toast("Menyiapkan unduhan…")
        AppScope.launch {
            try {
                val r = StreamResolver.resolve(track)
                val file = sanitize("${track.artist} - ${track.title}") + "." + r.ext
                val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val req = DownloadManager.Request(Uri.parse(r.url))
                    .setTitle(track.title)
                    .setDescription("ENJIWS Music")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "ENJIWS/$file")
                @Suppress("DEPRECATION")
                req.allowScanningByMediaScanner()
                dm.enqueue(req)
                UiBus.toast("Mengunduh “${track.title}” ke Musik/ENJIWS")
            } catch (e: Exception) {
                UiBus.toast("Gagal mengunduh: ${friendlyError(e)}")
            }
        }
    }

    /** Hapus file unduhan (di Musik/ENJIWS) dari MediaStore. */
    fun delete(context: Context, track: Track) {
        val app = context.applicationContext
        val uri = runCatching { Uri.parse(track.audioUrl) }.getOrNull() ?: return
        AppScope.launch {
            val removed = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.delete(uri, null, null) }.getOrDefault(0)
            }
            if (removed > 0) {
                LocalLibrary.refresh(app)
                UiBus.toast("Unduhan dihapus")
            } else {
                UiBus.toast("Tidak bisa dihapus dari aplikasi — hapus manual dari Files")
            }
        }
    }
}
