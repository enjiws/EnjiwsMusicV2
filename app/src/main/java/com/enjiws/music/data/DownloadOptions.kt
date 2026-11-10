package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/** Satu pilihan kualitas unduhan. [tag] ditempel di nama file supaya beberapa kualitas tidak saling timpa. */
data class DownloadOption(
    val title: String,
    val detail: String,
    val url: String,
    val ext: String,
    val tag: String = ""
)

/**
 * Daftar kualitas unduhan yang BENAR-BENAR tersedia untuk sebuah lagu, tanpa konversi palsu.
 *  - YouTube / SoundCloud: stream audio asli (lossy) dengan format dan bitrate yang terbaca.
 *  - Server Subsonic/Navidrome: file asli (Lossless / Hi-Res bila memang begitu) + MP3 hasil konversi server.
 */
object DownloadOptions {

    suspend fun forTrack(t: Track): List<DownloadOption> = withContext(Dispatchers.IO) {
        val list = when {
            t.subId.isNotBlank() -> Subsonic.downloadOptions(t.subId)
            t.youtubeUrl.isNotBlank() -> fromNewPipe(ServiceList.YouTube, t.youtubeUrl)
            t.soundcloudUrl.isNotBlank() -> fromNewPipe(ServiceList.SoundCloud, t.soundcloudUrl)
            else -> {
                val r = StreamResolver.resolve(t)
                listOf(DownloadOption("Standar", r.ext.uppercase(), r.url, r.ext))
            }
        }
        if (list.isEmpty()) throw IllegalStateException("Tidak ada pilihan kualitas untuk lagu ini")
        list
    }

    private fun fromNewPipe(service: StreamingService, url: String): List<DownloadOption> {
        val extractor = service.getStreamExtractor(url)
        extractor.fetchPage()
        val streams = extractor.getAudioStreams()
            .filter { it.isUrl() && it.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.getAverageBitrate() }
            .distinctBy { "${it.getFormat()}-${it.getAverageBitrate()}" }
        if (streams.isEmpty()) throw IllegalStateException("Tidak ada stream audio yang bisa diunduh")
        return streams.mapIndexed { i, s ->
            val (name, ext) = when (s.getFormat()) {
                MediaFormat.M4A -> "AAC" to "m4a"
                MediaFormat.MP3 -> "MP3" to "mp3"
                MediaFormat.OPUS -> "OPUS" to "opus"
                MediaFormat.WEBMA -> "OPUS" to "webm"
                else -> "Audio" to "m4a"
            }
            val kbps = s.getAverageBitrate().coerceAtLeast(0)
            val title = when {
                streams.size == 1 -> "Standar"
                i == 0 -> "Terbaik"
                i == streams.lastIndex -> "Hemat"
                else -> "Standar"
            }
            DownloadOption(
                title = title,
                detail = listOfNotNull(name, if (kbps > 0) "$kbps kbps" else null, "lossy").joinToString(" · "),
                url = s.getContent(),
                ext = ext,
                tag = name.lowercase() + if (kbps > 0) "$kbps" else ""
            )
        }
    }
}
