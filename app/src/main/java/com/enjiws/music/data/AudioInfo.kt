package com.enjiws.music.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Info teknis audio sumber (bukan output). Angka 0 = tidak diketahui. */
data class AudioInfo(
    val format: String,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val kbps: Int = 0,
    val channels: Int = 0,
    val lossless: Boolean = false
) {
    /** Hi-Res = lossless dan lebih dari CD/16-bit atau di atas 48 kHz. */
    val hiRes: Boolean get() = lossless && (bitDepth > 16 || sampleRateHz > 48_000)

    val tier: String get() = when { hiRes -> "Hi-Res Lossless"; lossless -> "Lossless"; else -> "" }

    /** Contoh: "FLAC · 24-bit/96 kHz" atau "OPUS · 160 kbps". */
    val detail: String
        get() {
            val parts = mutableListOf(format)
            when {
                bitDepth > 0 && sampleRateHz > 0 -> parts += "$bitDepth-bit/${khz(sampleRateHz)} kHz"
                sampleRateHz > 0 -> parts += "${khz(sampleRateHz)} kHz"
                kbps > 0 -> parts += "$kbps kbps"
            }
            return parts.joinToString(" · ")
        }
}

fun khz(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(hz / 1000.0)

/** Menyimpan info audio per lagu. Lagu online diisi oleh StreamResolver, lagu lokal dibaca dari file. */
object AudioInfoRepo {
    val info = mutableStateMapOf<String, AudioInfo>()
    private val probing = ConcurrentHashMap.newKeySet<String>()

    suspend fun ensure(context: Context, t: Track) {
        if (!t.isLocal || t.audioUrl.isBlank()) return
        val ref = StreamResolver.refOf(t)
        if (info.containsKey(ref) || !probing.add(ref)) return
        try {
            val r = withContext(Dispatchers.IO) { runCatching { probeLocal(context.applicationContext, Uri.parse(t.audioUrl)) }.getOrNull() }
            if (r != null) info[ref] = r
        } finally {
            probing.remove(ref)
        }
    }

    private fun describe(mime: String): Pair<String, Boolean> = when (mime.lowercase()) {
        "audio/flac" -> "FLAC" to true
        "audio/alac" -> "ALAC" to true
        "audio/raw", "audio/x-wav", "audio/wav", "audio/vnd.wave" -> "WAV" to true
        "audio/mpeg" -> "MP3" to false
        "audio/mp4a-latm" -> "AAC" to false
        "audio/opus" -> "Opus" to false
        "audio/vorbis" -> "Vorbis" to false
        else -> mime.substringAfter('/').uppercase() to false
    }

    private fun probeLocal(context: Context, uri: Uri): AudioInfo? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val rate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
                val ch = if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
                var kbps = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 else 0
                var bits = 0
                if (f.containsKey("bits-per-sample")) {
                    bits = f.getInteger("bits-per-sample")
                } else if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    bits = when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) { 3 -> 8; 2 -> 16; 21 -> 24; 22, 4 -> 32; else -> 0 }
                }
                val (name, lossless) = describe(mime)
                if (bits == 0 && lossless && android.os.Build.VERSION.SDK_INT >= 31) {
                    // 39 = METADATA_KEY_BITS_PER_SAMPLE (Android 12+)
                    bits = runCatching {
                        val mmr = MediaMetadataRetriever()
                        try { mmr.setDataSource(context, uri); mmr.extractMetadata(39)?.toIntOrNull() ?: 0 } finally { mmr.release() }
                    }.getOrDefault(0)
                }
                if (kbps == 0 && android.os.Build.VERSION.SDK_INT >= 30) {
                    // 20 = METADATA_KEY_BITRATE
                    kbps = runCatching {
                        val mmr = MediaMetadataRetriever()
                        try { mmr.setDataSource(context, uri); (mmr.extractMetadata(20)?.toIntOrNull() ?: 0) / 1000 } finally { mmr.release() }
                    }.getOrDefault(0)
                }
                return AudioInfo(name, rate, bits, kbps, ch, lossless)
            }
        } finally {
            ex.release()
        }
        return null
    }
}
