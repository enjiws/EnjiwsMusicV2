package com.enjiws.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

// Piped = frontend YouTube. Yang menghubungi YouTube adalah server Piped, jadi
// IP tunnel pengguna tidak terlihat oleh YouTube dan tidak kena bot check.
// Beberapa API Piped publik dicoba berurutan; kalau satu gagal/tidak menjawab,
// pindah ke berikutnya. Semua request memakai httpClient aplikasi (DoH),
// jadi tetap jalan meskipun DNS sistem bermasalah saat bypass.
val pipedApiInstances = listOf(
    "https://pipedapi.kavin.rocks",
    "https://pipedapi.adminforge.de",
    "https://pipedapi.leptons.xyz"
)

// Cari lagu di satu instance. Null = instance gagal (coba berikutnya).
// Sukses dengan hasil kosong tetap dianggap sukses (lagu memang tidak ada).
private fun searchPipedOnInstance(base: String, query: String): List<OnlineTrack>? {
    return try {
        val url = "$base/search?q=" + URLEncoder.encode(query, "UTF-8") + "&filter=music_songs"
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) return null
        val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items") ?: return null
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.optString("type") != "stream") return@mapNotNull null
            val videoId = o.optString("url").substringAfter("v=", "").substringBefore("&")
            if (videoId.isBlank()) return@mapNotNull null
            OnlineTrack(
                title = o.optString("title"),
                artist = o.optString("uploaderName"),
                artworkUrl = o.optString("thumbnail"),
                durationSeconds = o.optLong("duration"),
                source = "Piped",
                pipedId = videoId
            )
        }.filter { it.title.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

suspend fun searchPiped(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
    for (base in pipedApiInstances) {
        val result = searchPipedOnInstance(base, query)
        if (result != null) return@withContext result
    }
    throw IllegalStateException("Semua server Piped gagal dihubungi")
}

// Ambil URL audio terbaik (bitrate tertinggi) untuk satu video di satu instance.
private fun resolvePipedOnInstance(base: String, videoId: String): Pair<String, String>? {
    return try {
        val resp = httpClient.newCall(Request.Builder().url("$base/streams/$videoId").build()).execute()
        if (!resp.isSuccessful) return null
        val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("audioStreams") ?: return null
        var bestUrl = ""
        var bestBitrate = -1L
        var bestExt = "m4a"
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("url")
            if (u.isBlank()) continue
            val bitrate = o.optLong("bitrate")
            if (bitrate > bestBitrate) {
                bestBitrate = bitrate
                bestUrl = u
                bestExt = if (o.optString("mimeType").contains("webm")) "webm" else "m4a"
            }
        }
        if (bestUrl.isBlank()) null else bestUrl to bestExt
    } catch (e: Exception) {
        null
    }
}

suspend fun resolvePipedStreamDetailed(videoId: String): Pair<String, String> = withContext(Dispatchers.IO) {
    for (base in pipedApiInstances) {
        val result = resolvePipedOnInstance(base, videoId)
        if (result != null) return@withContext result
    }
    throw IllegalStateException("Semua server Piped gagal dihubungi")
}

suspend fun resolvePipedStream(videoId: String): String = resolvePipedStreamDetailed(videoId).first
