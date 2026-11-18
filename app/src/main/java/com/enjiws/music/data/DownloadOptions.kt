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
    val tag: String = "",
    val estimatedSizeBytes: Long? = null
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
        list.map { option ->
            if (option.estimatedSizeBytes != null) option else {
                val kbps = Regex("(\\d+)\\s*kbps", RegexOption.IGNORE_CASE).find(option.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                option.copy(estimatedSizeBytes = estimateAudioBytes(t.durationSeconds, kbps))
            }
        }
    }

    /** Pilih opsi terdekat dengan target pengguna; fallback tetap ke opsi sumber terbaik. */
    fun preferred(options: List<DownloadOption>, target: AudioQuality): DownloadOption {
        if (options.isEmpty()) throw IllegalArgumentException("Daftar opsi unduhan kosong")
        val lossless = options.filter { it.detail.contains("file asli", true) || it.title.contains("lossless", true) || it.title.contains("hi-res", true) }
        val hiRes = lossless.filter { it.title.contains("hi-res", true) }
        val lossy = options.filter { it.detail.contains("lossy", true) || it.detail.contains("mp3", true) || it.detail.contains("aac", true) || it.detail.contains("opus", true) }
        return when (target) {
            AudioQuality.STANDARD -> lossy.firstOrNull() ?: options.last()
            AudioQuality.LOSSLESS -> lossless.firstOrNull() ?: options.first()
            AudioQuality.HI_RES -> hiRes.firstOrNull() ?: lossless.firstOrNull() ?: options.first()
            AudioQuality.HI_RES_MAX -> hiRes.firstOrNull { it.detail.contains("192", true) || it.title.contains("max", true) } ?: hiRes.firstOrNull() ?: lossless.firstOrNull() ?: options.first()
        }
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
                tag = name.lowercase() + if (kbps > 0) "$kbps" else "",
                estimatedSizeBytes = null
            )
        }
    }
}
