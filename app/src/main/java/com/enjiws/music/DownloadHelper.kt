package com.enjiws.music

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

// URL stream YouTube/SoundCloud kedaluwarsa dalam beberapa jam: selalu ambil
// ulang sesaat sebelum diputar atau diunduh.
suspend fun resolveStreamAudio(service: StreamingService, pageUrl: String): String =
    resolveStreamAudioDetailed(service, pageUrl).first

// Sama seperti di atas, tapi ikut mengembalikan ekstensi file (m4a/mp3/dll)
// supaya hasil unduhan tersimpan dengan format yang benar.
suspend fun resolveStreamAudioDetailed(service: StreamingService, pageUrl: String): Pair<String, String> =
    withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(service, pageUrl)
        val all = info.audioStreams
        val usable = all.filter { it.isUrl }
        val progressive = usable.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = progressive.filter { it.format?.name == "M4A" || it.format?.name == "MP3" }
            .maxByOrNull { it.averageBitrate }
            ?: progressive.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException(
                if (usable.isNotEmpty()) "Hanya tersedia stream HLS/DASH (butuh modul media3-exoplayer-hls)"
                else "audio=${all.size}, url=${usable.size}, hls=${!info.hlsUrl.isNullOrBlank()}, dash=${!info.dashMpdUrl.isNullOrBlank()}"
            )
        val ext = when (best.format?.name) {
            "M4A" -> "m4a"
            "MP3" -> "mp3"
            "WEBMA" -> "webm"
            "OPUS" -> "opus"
            else -> "mp3"
        }
        best.content to ext
    }

fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifBlank { "track" }.take(100)

// Unduhan jalan di background lewat DownloadManager (dengan notifikasi
// progres dari sistem), jadi tetap bisa sambil memutar lagu lain.
fun enqueueDownload(context: Context, title: String, artist: String, url: String, ext: String) {
    val fileName = sanitizeFileName("$artist - $title") + ".$ext"
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription("ENJIWS Music")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "ENJIWS/$fileName")
    dm.enqueue(request)
}
