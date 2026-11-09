package com.enjiws.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.enjiws.music.data.Track
import org.json.JSONObject

private const val EXTRA_TRACK = "track"
const val RESOLVE_SCHEME = "enjiws"

/**
 * Lagu online tidak diberi URL stream langsung (cepat kedaluwarsa). Sebagai gantinya URI-nya
 * `enjiws://resolve?...` dan TrackResolver di service mengubahnya jadi URL asli tepat saat dimuat.
 */
fun Track.toMediaItem(): MediaItem {
    val needsResolve = !isLocal && (youtubeUrl.isNotBlank() || soundcloudUrl.isNotBlank() || pipedId.isNotBlank() || subId.isNotBlank())
    val uri = if (needsResolve) {
        Uri.Builder().scheme(RESOLVE_SCHEME).authority("resolve")
            .appendQueryParameter("yt", youtubeUrl)
            .appendQueryParameter("sc", soundcloudUrl)
            .appendQueryParameter("pp", pipedId)
            .appendQueryParameter("sub", subId)
            .build().toString()
    } else audioUrl
    val extras = Bundle().apply { putString(EXTRA_TRACK, toJson().toString()) }
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album.ifBlank { null })
                .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                .setExtras(extras)
                .build()
        )
        .build()
}

fun MediaItem.toTrack(): Track? {
    val raw = mediaMetadata.extras?.getString(EXTRA_TRACK)
    if (raw != null) runCatching { return Track.fromJson(JSONObject(raw)) }
    val title = mediaMetadata.title?.toString() ?: return null
    return Track(
        title = title,
        artist = mediaMetadata.artist?.toString().orEmpty(),
        artworkUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
        source = "?",
        audioUrl = localConfiguration?.uri?.toString().orEmpty()
    )
}

/** Susun ulang Track minimal dari URI `enjiws://resolve?...` (cukup untuk resolve stream). */
fun trackFromResolveUri(uri: Uri): Track = Track(
    title = "", artist = "",
    youtubeUrl = uri.getQueryParameter("yt").orEmpty(),
    soundcloudUrl = uri.getQueryParameter("sc").orEmpty(),
    pipedId = uri.getQueryParameter("pp").orEmpty(),
    subId = uri.getQueryParameter("sub").orEmpty()
)
