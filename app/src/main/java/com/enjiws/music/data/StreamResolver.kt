package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.mutableStateMapOf

data class Resolved(val url: String, val ext: String, val kbps: Int = 0, val label: String = "", val info: AudioInfo? = null)

/**
 * Mengubah Track online jadi URL audio yang bisa diputar. URL stream YouTube/SoundCloud kedaluwarsa
 * dalam hitungan jam, jadi di-resolve saat dibutuhkan (lazy) dan di-cache sebentar supaya seek
 * (yang membuka ulang koneksi) tidak memicu resolve berulang.
 */
object StreamResolver {
    private const val TTL_MS = 20 * 60_000L
    private val cache = ConcurrentHashMap<String, Pair<Resolved, Long>>()

    /** Label kualitas per lagu (mis. "OPUS · 160 kbps"), dibaca UI untuk badge di pemutar. */
    val quality = mutableStateMapOf<String, String>()

    /** Kunci yang sama untuk Track lengkap maupun Track minimal dari URI resolve. */
    fun refOf(t: Track): String = t.youtubeUrl.ifBlank { t.soundcloudUrl.ifBlank { t.pipedId.ifBlank { t.subId.ifBlank { t.audioUrl } } } }

    private fun cacheKey(t: Track) = "${t.youtubeUrl}|${t.soundcloudUrl}|${t.pipedId}|${t.subId}|${t.audioUrl}|${Prefs.dataSaver}"

    private fun formatName(ext: String): String = when (ext.lowercase()) {
        "m4a", "aac" -> "AAC"
        "webm", "opus" -> "OPUS"
        "mp3" -> "MP3"
        else -> ext.uppercase()
    }

    fun invalidate(t: Track) { cache.remove(cacheKey(t)) }

    /** Dipanggil dari thread loader ExoPlayer (boleh memblokir). */
    fun resolveBlocking(t: Track): Resolved = runBlocking(Dispatchers.IO) { resolve(t) }

    suspend fun resolve(t: Track): Resolved {
        val k = cacheKey(t)
        cache[k]?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first }
        val r = when {
            t.soundcloudUrl.isNotBlank() -> fromNewPipe(ServiceList.SoundCloud, t.soundcloudUrl)
            t.youtubeUrl.isNotBlank() -> try {
                fromNewPipe(ServiceList.YouTube, t.youtubeUrl)
            } catch (e: Exception) {
                val id = Piped.videoIdOf(t.youtubeUrl)
                if (id.isBlank()) throw e
                // YouTube menolak (bot check / mesin ketinggalan) -> coba lewat Piped.
                runCatching { withContext(Dispatchers.IO) { Piped.stream(id) }.let { Resolved(it.first, it.second) } }
                    .getOrElse { throw e }
            }
            t.pipedId.isNotBlank() -> withContext(Dispatchers.IO) { Piped.stream(t.pipedId) }.let { Resolved(it.first, it.second) }
            t.subId.isNotBlank() -> withContext(Dispatchers.IO) { Subsonic.resolve(t.subId) }
            t.audioUrl.isNotBlank() -> Resolved(t.audioUrl, "mp3")
            else -> throw IllegalStateException("Lagu ini tidak bisa diputar")
        }
        cache[k] = r to System.currentTimeMillis() + TTL_MS
        quality[refOf(t)] = when {
            r.label.isNotBlank() -> r.label
            r.kbps > 0 -> "${r.ext.uppercase()} · ${r.kbps} kbps"
            else -> r.ext.uppercase()
        }
        AudioInfoRepo.info[refOf(t)] = r.info ?: AudioInfo(format = formatName(r.ext), kbps = r.kbps)
        return r
    }

    private suspend fun fromNewPipe(service: StreamingService, pageUrl: String): Resolved =
        withContext(Dispatchers.IO) {
            // Extractor langsung (bukan StreamInfo.getInfo) -> tidak ikut mengurai lagu terkait & metadata.
            val extractor = service.getStreamExtractor(pageUrl)
            extractor.fetchPage()
            val all = extractor.getAudioStreams()
            val usable = all.filter { it.isUrl() }
            val progressive = usable.filter { it.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP }
            // Semua format progresif ikut dipertimbangkan (termasuk Opus), lalu diambil bitrate tertinggi.
            val compatible = progressive
            val best = if (Prefs.dataSaver) {
                compatible.filter { it.getAverageBitrate() > 0 }.minByOrNull { it.getAverageBitrate() }
                    ?: compatible.firstOrNull()
            } else compatible.maxByOrNull { it.getAverageBitrate() }
            val chosen = best ?: throw IllegalStateException(
                if (usable.isNotEmpty()) "Hanya tersedia stream HLS/DASH" else "Could not get any stream"
            )
            val ext = when (chosen.getFormat()) {
                MediaFormat.M4A -> "m4a"
                MediaFormat.MP3 -> "mp3"
                MediaFormat.WEBMA -> "webm"
                MediaFormat.OPUS -> "opus"
                else -> "mp3"
            }
            Resolved(chosen.getContent(), ext, chosen.getAverageBitrate().coerceAtLeast(0))
        }
}

fun isNetworkError(e: Throwable?): Boolean {
    var c = e
    var depth = 0
    while (c != null && depth++ < 8) {
        if (c is UnknownHostException || c is SocketTimeoutException || c is ConnectException) return true
        c = c.cause
    }
    return false
}

/** Pesan error teknis (umumnya dari NewPipe, bahasa Inggris) jadi bahasa Indonesia yang jelas. */
fun friendlyError(e: Throwable): String {
    if (isNetworkError(e)) return "koneksi internet bermasalah"
    val msg = e.message ?: ""
    return when {
        msg.contains("Could not get any stream") -> "lagu ini tidak menyediakan link audio yang bisa diambil"
        msg.contains("HLS/DASH") -> "lagu ini hanya tersedia sebagai stream HLS/DASH yang belum didukung"
        msg.contains("429") || msg.contains("Too Many Requests", true) -> "server membatasi permintaan, coba lagi sebentar"
        msg.contains("not a bot", true) -> "YouTube mengira aplikasi ini bot (sering karena VPN aktif)"
        msg.contains("needs to be reloaded", true) -> "YouTube gagal memuat video, coba lagi nanti"
        msg.isBlank() -> "coba lagi"
        else -> msg
    }
}
