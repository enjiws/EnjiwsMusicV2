package com.enjiws.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

// Lagu terkait dari Piped: endpoint /streams mengembalikan relatedStreams
// dengan skema yang sama seperti hasil pencarian.
private fun fetchPipedRelatedOnInstance(base: String, videoId: String): List<OnlineTrack>? {
    return try {
        val resp = httpClient.newCall(Request.Builder().url("$base/streams/$videoId").build()).execute()
        if (!resp.isSuccessful) return null
        val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("relatedStreams") ?: return null
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type")
            if (type.isNotBlank() && type != "stream") return@mapNotNull null
            val id = o.optString("url").substringAfter("v=", "").substringBefore("&")
            if (id.isBlank()) return@mapNotNull null
            OnlineTrack(
                title = o.optString("title"),
                artist = o.optString("uploaderName"),
                artworkUrl = o.optString("thumbnail"),
                durationSeconds = o.optLong("duration"),
                source = "Piped",
                pipedId = id
            )
        }.filter { it.title.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

suspend fun fetchPipedRelated(videoId: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    for (base in pipedApiInstances) {
        val result = fetchPipedRelatedOnInstance(base, videoId)
        if (result != null) return@withContext result
    }
    throw IllegalStateException("Piped gagal")
}

// Lagu terkait untuk satu lagu (radio otomatis saat antrean habis):
// - Lagu Piped -> related dari Piped.
// - Lagu YouTube -> related dari NewPipe; kalau kena bot check / gagal,
//   jatuh ke related Piped dari videoId yang sama.
// - Sumber lain (Apple/Audius) -> tidak ada related, antrean berhenti.
suspend fun fetchRelatedTracks(track: OnlineTrack): List<OnlineTrack> = withContext(Dispatchers.IO) {
    if (track.pipedId.isNotBlank()) {
        return@withContext try { fetchPipedRelated(track.pipedId) } catch (e: Exception) { emptyList() }
    }
    val videoId = track.youtubeUrl.substringAfter("v=", "").substringBefore("&")
    if (videoId.isBlank()) return@withContext emptyList()
    try {
        val extractor = ServiceList.YouTube.getStreamExtractor(track.youtubeUrl)
        extractor.fetchPage()
        val related = StreamInfo.getInfo(extractor).getRelatedItems()
            ?.filterIsInstance<StreamInfoItem>().orEmpty()
        val mapped = related.mapNotNull { item ->
            val thumbs = item.getThumbnails()
            val thumb = thumbs.maxByOrNull { it.getHeight() }?.getUrl()
                ?: thumbs.firstOrNull()?.getUrl() ?: ""
            val url = item.getUrl() ?: return@mapNotNull null
            OnlineTrack(
                title = item.getName() ?: "",
                artist = item.getUploaderName() ?: "",
                artworkUrl = thumb,
                durationSeconds = item.getDuration(),
                source = "YouTube",
                youtubeUrl = url
            )
        }.filter { it.title.isNotBlank() }
        if (mapped.isNotEmpty()) return@withContext mapped
    } catch (e: Exception) {
        // Kena bot check / gagal -> lanjut ke Piped di bawah.
    }
    try { fetchPipedRelated(videoId) } catch (e: Exception) { emptyList() }
}
