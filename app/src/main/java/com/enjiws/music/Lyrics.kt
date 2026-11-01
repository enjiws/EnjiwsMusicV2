package com.enjiws.music

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

val httpClient = OkHttpClient()

data class LyricLine(val text: String, val timeMs: Long)

// Kandidat pencarian lirik: (artis, judul) apa adanya, plus hasil pecahan
// "Artis - Judul" dari judul lagu. Pecahan ini penting karena judul dari
// YouTube/Piped sering berbentuk "Artis - Judul" sementara kolom artisnya
// adalah nama channel/uploader (bukan artis asli), sehingga pencarian
// langsung dengan pasangan itu gagal di LRCLIB.
private fun lyricCandidates(artist: String, title: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    out += artist to title
    val dash = title.indexOf(" - ")
    if (dash > 0) {
        out += title.substring(0, dash).trim() to title.substring(dash + 3).trim()
    }
    return out.distinct()
}

private fun lrclibGet(artist: String, title: String): JSONObject? {
    return try {
        val url = "https://lrclib.net/api/get" +
            "?artist_name=" + URLEncoder.encode(artist, "UTF-8") +
            "&track_name=" + URLEncoder.encode(title, "UTF-8")
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) return null
        JSONObject(resp.body?.string().orEmpty())
    } catch (e: Exception) {
        null
    }
}

private fun lrclibGetById(id: Long): JSONObject? {
    return try {
        val url = "https://lrclib.net/api/get?id=$id"
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) return null
        JSONObject(resp.body?.string().orEmpty())
    } catch (e: Exception) {
        null
    }
}

// Hasil pencarian LRCLIB. Format jawabannya bisa berupa array langsung
// maupun objek berisi "results" — keduanya ditangani.
private fun parseSearchResults(body: String): List<JSONObject> {
    try {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    } catch (e: Exception) {
        // Bukan array, coba sebagai objek.
    }
    return try {
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

// Cari di LRCLIB (pencarian fuzzy). Kembalikan objek hasil pertama yang
// punya lirik yang diminta (tersinkron atau biasa). Kalau hasil search
// hanya berisi metadata, ambil lirik lengkapnya lewat get by id.
private fun lrclibSearch(artist: String, title: String, needSynced: Boolean): JSONObject? {
    return try {
        val url = "https://lrclib.net/api/search" +
            "?artist_name=" + URLEncoder.encode(artist, "UTF-8") +
            "&track_name=" + URLEncoder.encode(title, "UTF-8")
        val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) return null
        val field = if (needSynced) "syncedLyrics" else "plainLyrics"
        for (o in parseSearchResults(resp.body?.string().orEmpty())) {
            if (o.optString(field).isNotBlank()) return o
            val id = o.optLong("id")
            if (id > 0) {
                val full = lrclibGetById(id)
                if (full != null && full.optString(field).isNotBlank()) return full
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

private fun parseSynced(synced: String): List<LyricLine> {
    val lineRegex = Regex("""\[(\d+):(\d+\.\d+)\]\s*(.*)""")
    return synced.lines().mapNotNull { line ->
        val m = lineRegex.find(line) ?: return@mapNotNull null
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].toDouble()
        LyricLine(m.groupValues[3], (minutes * 60 * 1000 + seconds * 1000).toLong())
    }
}

// Ambil lirik tersinkron dari LRCLIB (gratis, tanpa API key).
// Urutan: get langsung per kandidat -> search LRCLIB per kandidat.
// List kosong kalau tidak ada / gagal.
suspend fun fetchLyrics(artist: String, title: String): List<LyricLine> = withContext(Dispatchers.IO) {
    if (artist.isBlank() && title.isBlank()) return@withContext emptyList()
    val candidates = lyricCandidates(artist, title)
    for ((a, t) in candidates) {
        val synced = lrclibGet(a, t)?.optString("syncedLyrics").orEmpty()
        if (synced.isNotBlank()) {
            val parsed = parseSynced(synced)
            if (parsed.isNotEmpty()) return@withContext parsed
        }
    }
    for ((a, t) in candidates) {
        val found = lrclibSearch(a, t, needSynced = true) ?: continue
        val parsed = parseSynced(found.optString("syncedLyrics"))
        if (parsed.isNotEmpty()) return@withContext parsed
    }
    emptyList()
}

// Ambil lirik biasa (tanpa sinkronisasi) sebagai cadangan kalau lirik
// tersinkron tidak ada. Urutan: plainLyrics LRCLIB (get lalu search)
// -> lyrics.ovh. Null kalau semuanya gagal.
suspend fun fetchPlainLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
    if (artist.isBlank() && title.isBlank()) return@withContext null
    val candidates = lyricCandidates(artist, title)
    for ((a, t) in candidates) {
        val plain = lrclibGet(a, t)?.optString("plainLyrics").orEmpty()
        if (plain.isNotBlank()) return@withContext plain
    }
    for ((a, t) in candidates) {
        val found = lrclibSearch(a, t, needSynced = false) ?: continue
        val plain = found.optString("plainLyrics")
        if (plain.isNotBlank()) return@withContext plain
    }
    for ((a, t) in candidates) {
        try {
            val url = "https://api.lyrics.ovh/v1/" + Uri.encode(a) + "/" + Uri.encode(t)
            val resp = httpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) continue
            val lyrics = JSONObject(resp.body?.string().orEmpty()).optString("lyrics")
            if (lyrics.isNotBlank()) return@withContext lyrics
        } catch (e: Exception) {
            // Coba kandidat berikutnya.
        }
    }
    null
}
