package com.enjiws.music.data

import android.net.Uri
import android.os.Build
import org.json.JSONObject

const val SOURCE_LOCAL = "Lokal"

/** Satu lagu, baik dari perangkat maupun dari sumber online. Skema JSON-nya kompatibel dengan versi 1.x. */
data class Track(
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val durationSeconds: Long = 0L,
    val source: String = "",
    val audioUrl: String = "",
    val youtubeUrl: String = "",
    val soundcloudUrl: String = "",
    val storeUrl: String = "",
    val pipedId: String = "",
    val album: String = "",
    val localId: Long = 0L
) {
    val isLocal: Boolean get() = source == SOURCE_LOCAL
    val isPreview: Boolean get() = source == "Apple Music"
    val canStream: Boolean
        get() = audioUrl.isNotBlank() || youtubeUrl.isNotBlank() || soundcloudUrl.isNotBlank() || pipedId.isNotBlank()

    val key: String
        get() {
            val ref = youtubeUrl.ifBlank { soundcloudUrl.ifBlank { pipedId.ifBlank { audioUrl } } }
            return "$source|$ref|$title|$artist"
        }

    /** ID stabil untuk MediaItem. */
    val id: String get() = java.lang.Long.toHexString(key.hashCode().toLong() and 0xffffffffL)

    /** Model gambar untuk Coil. Lagu lokal di Android 10+ memakai thumbnail langsung dari file. */
    val cover: Any?
        get() = when {
            isLocal && Build.VERSION.SDK_INT >= 29 && audioUrl.isNotBlank() -> Uri.parse(audioUrl)
            artworkUrl.isNotBlank() -> artworkUrl
            else -> null
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title); put("artist", artist); put("artworkUrl", artworkUrl)
        put("durationSeconds", durationSeconds); put("source", source); put("audioUrl", audioUrl)
        put("youtubeUrl", youtubeUrl); put("soundcloudUrl", soundcloudUrl); put("storeUrl", storeUrl)
        put("pipedId", pipedId); put("album", album); put("localId", localId)
    }

    companion object {
        fun fromJson(o: JSONObject) = Track(
            title = o.optString("title"), artist = o.optString("artist"),
            artworkUrl = o.optString("artworkUrl"), durationSeconds = o.optLong("durationSeconds"),
            source = o.optString("source"), audioUrl = o.optString("audioUrl"),
            youtubeUrl = o.optString("youtubeUrl"), soundcloudUrl = o.optString("soundcloudUrl"),
            storeUrl = o.optString("storeUrl"), pipedId = o.optString("pipedId"),
            album = o.optString("album"), localId = o.optLong("localId")
        )
    }
}

/** Dua lagu dianggap sama kalau identitasnya sama, atau judul+artisnya sama. */
fun isSameSong(a: Track, b: Track): Boolean =
    a.key == b.key || (a.title.isNotBlank() &&
        a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
        a.artist.trim().equals(b.artist.trim(), ignoreCase = true))

data class Playlist(val id: String, val name: String, val tracks: List<Track>)

data class HistoryEntry(val track: Track, val playedAt: Long, val plays: Int)

data class LyricLine(val text: String, val timeMs: Long)
data class Lyrics(val synced: List<LyricLine> = emptyList(), val plain: String = "") {
    val isEmpty: Boolean get() = synced.isEmpty() && plain.isBlank()
}

enum class Source(val label: String) {
    YouTube("YouTube"), SoundCloud("SoundCloud"), Audius("Audius"), Apple("Apple Music"), Piped("Piped")
}
