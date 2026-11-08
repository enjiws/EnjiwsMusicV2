package com.enjiws.music.data

import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Lirik dari LRCLIB (tersinkron / biasa), cadangan lyrics.ovh. Hasil di-cache di memori. */
object LyricsRepo {
    private val cache = LruCache<String, Lyrics>(60)

    suspend fun get(track: Track): Lyrics {
        cache.get(track.id)?.let { return it }
        val result = withContext(Dispatchers.IO) { fetch(track) }
        // Hanya cache hasil yang ada isinya; gagal jaringan boleh dicoba lagi nanti.
        if (!result.isEmpty) cache.put(track.id, result)
        return result
    }

    private val noise = Regex(
        """\s*[\(\[][^\)\]]*(official|video|audio|lyrics?|lirik|mv|hd|hq|remaster|visualizer)[^\)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    private fun clean(s: String) = noise.replace(s, "").trim()

    // Judul YouTube sering "Artis - Judul" sementara kolom artis berisi nama channel.
    private fun candidates(artist: String, title: String): List<Pair<String, String>> {
        val t = clean(title)
        val out = mutableListOf(artist.trim() to t)
        val dash = t.indexOf(" - ")
        if (dash > 0) out += t.substring(0, dash).trim() to t.substring(dash + 3).trim()
        return out.filter { it.second.isNotBlank() }.distinct()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getObj(url: String): JSONObject? =
        Http.getString(url)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getArr(url: String): List<JSONObject> {
        val body = Http.getString(url) ?: return emptyList()
        return runCatching {
            val a = JSONArray(body)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun fetch(track: Track): Lyrics {
        if (track.title.isBlank()) return Lyrics()
        val cands = candidates(track.artist, track.title)
        val dur = if (track.durationSeconds > 0) "&duration=${track.durationSeconds}" else ""
        var plain = ""

        for ((a, t) in cands) {
            val o = getObj("https://lrclib.net/api/get?artist_name=${enc(a)}&track_name=${enc(t)}$dur") ?: continue
            parseSynced(o.optString("syncedLyrics")).takeIf { it.isNotEmpty() }?.let { return Lyrics(synced = it) }
            if (plain.isBlank()) plain = o.optString("plainLyrics")
        }
        val searches = cands.map { (a, t) -> "https://lrclib.net/api/search?artist_name=${enc(a)}&track_name=${enc(t)}" } +
            cands.map { (_, t) -> "https://lrclib.net/api/search?q=${enc(t)}" }
        for (url in searches) {
            val results = getArr(url)
            for (o in results.take(5)) {
                parseSynced(o.optString("syncedLyrics")).takeIf { it.isNotEmpty() }?.let { return Lyrics(synced = it) }
                if (plain.isBlank()) plain = o.optString("plainLyrics")
            }
        }
        if (plain.isNotBlank()) return Lyrics(plain = plain)

        for ((a, t) in cands) {
            val txt = getObj("https://api.lyrics.ovh/v1/${Uri.encode(a)}/${Uri.encode(t)}")?.optString("lyrics").orEmpty()
            if (txt.isNotBlank()) return Lyrics(plain = txt.trim())
        }
        return Lyrics()
    }

    // Mendukung [mm:ss], [mm:ss.xx], [mm:ss:xxx] dan multi timestamp per baris.
    private val ts = Regex("""\[(\d+):(\d{2})(?:[.:](\d{1,3}))?\]""")

    private fun parseSynced(synced: String): List<LyricLine> {
        if (synced.isBlank()) return emptyList()
        return synced.lines().flatMap { line ->
            val matches = ts.findAll(line).toList()
            if (matches.isEmpty()) return@flatMap emptyList<LyricLine>()
            val text = ts.replace(line, "").trim()
            matches.map { m ->
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; 3 -> frac.toLong(); else -> 0L }
                LyricLine(text, m.groupValues[1].toLong() * 60_000L + m.groupValues[2].toLong() * 1_000L + ms)
            }
        }.sortedBy { it.timeMs }
    }
}
