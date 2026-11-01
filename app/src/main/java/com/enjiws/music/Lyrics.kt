package com.enjiws.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

val httpClient = OkHttpClient()

data class LyricLine(val text: String, val timeMs: Long)

// Ambil lirik tersinkron dari LRCLIB (gratis, tanpa API key).
// Mengembalikan list kosong kalau tidak ada lirik / gagal — layar Now Playing
// menampilkan "Lirik tidak tersedia" dalam kondisi itu.
suspend fun fetchLyrics(artist: String, title: String): List<LyricLine> = withContext(Dispatchers.IO) {
    try {
        if (artist.isBlank() && title.isBlank()) return@withContext emptyList()
        val url = "https://lrclib.net/api/get" +
            "?artist_name=" + URLEncoder.encode(artist, "UTF-8") +
            "&track_name=" + URLEncoder.encode(title, "UTF-8")
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string().orEmpty()
        val synced = JSONObject(body).optString("syncedLyrics")
        if (synced.isBlank()) return@withContext emptyList()
        val lineRegex = Regex("""\[(\d+):(\d+\.\d+)\]\s*(.*)""")
        synced.lines().mapNotNull { line ->
            val m = lineRegex.find(line) ?: return@mapNotNull null
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toDouble()
            LyricLine(m.groupValues[3], (minutes * 60 * 1000 + seconds * 1000).toLong())
        }
    } catch (e: Exception) {
        emptyList()
    }
}
