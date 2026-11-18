package com.enjiws.music.data

/** Target kualitas pilihan pengguna. Ini preferensi, bukan klaim kualitas sumber. */
enum class AudioQuality(val label: String, val description: String) {
    STANDARD("Standard", "AAC / MP3 atau format lossy terbaik yang tersedia"),
    LOSSLESS("Lossless", "Utamakan file lossless asli; target CD 16-bit / 44.1 kHz"),
    HI_RES("Hi-Res", "Utamakan lossless di atas CD, hingga 24-bit / 96 kHz"),
    HI_RES_MAX("Hi-Res Max", "Utamakan sumber lossless tertinggi, hingga 24-bit / 192 kHz")
}

/** Mengelompokkan sumber berdasarkan metadata yang benar-benar diketahui. */
fun AudioInfo.qualityLabel(): String = when {
    !lossless -> if (format.isBlank()) "Kualitas tidak diketahui" else "Lossy · $format"
    hiRes && sampleRateHz >= 176_400 -> "Hi-Res Max"
    hiRes -> "Hi-Res Lossless"
    else -> "Lossless"
}

/** Estimasi kasar dari bitrate dan durasi. Null bila salah satu data tidak tersedia. */
fun estimateAudioBytes(durationSeconds: Long, bitrateKbps: Int): Long? {
    if (durationSeconds <= 0L || bitrateKbps <= 0) return null
    return durationSeconds * bitrateKbps.toLong() * 125L
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 0L) return "—"
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000.0) "%.2f GB".format(mb / 1000.0) else "%.1f MB".format(mb)
}
