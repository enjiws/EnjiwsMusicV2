package com.enjiws.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/**
 * Klien Subsonic API (Navidrome, Airsonic, Gonic, Subsonic, dll.) untuk server musik milik sendiri.
 * Audio diambil apa adanya (format=raw), jadi FLAC / Hi-Res tidak di-transcode.
 */
object Subsonic {
    val configured: Boolean
        get() = Prefs.serverUrl.isNotBlank() && Prefs.serverUser.isNotBlank() && Prefs.serverPass.isNotBlank()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Autentikasi token+salt (password tidak pernah dikirim polos). */
    private fun auth(): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        return "u=${enc(Prefs.serverUser)}&t=${md5(Prefs.serverPass + salt)}&s=$salt&v=1.16.1&c=EnjiwsMusic&f=json"
    }

    private fun url(endpoint: String, extra: String = ""): String {
        val base = Prefs.serverUrl.trim().trimEnd('/')
        return "$base/rest/$endpoint.view?${auth()}" + if (extra.isNotBlank()) "&$extra" else ""
    }

    private fun call(endpoint: String, extra: String = ""): JSONObject {
        val req = Request.Builder().url(url(endpoint, extra)).header("User-Agent", Http.UA).build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty()).getJSONObject("subsonic-response")
            if (root.optString("status") != "ok") {
                throw IOException(root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Server menolak permintaan" })
            }
            return root
        }
    }

    private fun toTrack(o: JSONObject): Track? {
        val id = o.optString("id")
        if (id.isBlank() || o.optBoolean("isDir", false)) return null
        val cover = o.optString("coverArt")
        return Track(
            title = o.optString("title").ifBlank { "Tanpa judul" },
            artist = o.optString("artist"),
            album = o.optString("album"),
            artworkUrl = if (cover.isNotBlank()) url("getCoverArt", "id=${enc(cover)}&size=600") else "",
            durationSeconds = o.optLong("duration"),
            source = "Server",
            subId = id
        )
    }

    private fun songs(arr: org.json.JSONArray?): List<Track> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { toTrack(it) } }
    }

    fun search(q: String): List<Track> {
        val r = call("search3", "query=${enc(q)}&songCount=40&albumCount=0&artistCount=0")
        return songs(r.optJSONObject("searchResult3")?.optJSONArray("song"))
    }

    /** Lagu serupa untuk radio; kalau server tidak punya datanya, pakai lagu acak dari pustaka. */
    fun related(id: String): List<Track> {
        val similar = runCatching { songs(call("getSimilarSongs2", "id=${enc(id)}&count=20").optJSONObject("similarSongs2")?.optJSONArray("song")) }
            .getOrDefault(emptyList())
        if (similar.isNotEmpty()) return similar
        return songs(call("getRandomSongs", "size=20").optJSONObject("randomSongs")?.optJSONArray("song"))
    }

    /** URL stream asli + info kualitas (FLAC 24-bit/96 kHz dll. bila server menyediakan). */
    fun resolve(id: String): Resolved {
        val song = runCatching { call("getSong", "id=${enc(id)}").optJSONObject("song") }.getOrNull()
        val suffix = song?.optString("suffix").orEmpty().ifBlank { "audio" }
        val kbps = song?.optInt("bitRate") ?: 0
        val bits = song?.optInt("bitDepth") ?: 0
        val hz = song?.optInt("samplingRate") ?: 0
        val label = if (bits > 0 && hz > 0) {
            val khz = if (hz % 1000 == 0) "${hz / 1000}" else "%.1f".format(hz / 1000.0)
            "${suffix.uppercase()} · $bits-bit/$khz kHz"
        } else ""
        val channels = song?.optInt("channelCount") ?: 0
        val lossless = suffix.lowercase() in setOf("flac", "alac", "wav", "wave", "aiff", "aif", "ape", "wv", "dsf", "dff")
        val info = AudioInfo(format = suffix.uppercase(), sampleRateHz = hz, bitDepth = bits, kbps = kbps, channels = channels, lossless = lossless)
        return Resolved(url("stream", "id=${enc(id)}&format=raw"), suffix, kbps, label, info)
    }

    /** Pilihan unduhan: file asli apa adanya (Lossless / Hi-Res bila memang begitu) + MP3 hasil konversi server. */
    fun downloadOptions(id: String): List<DownloadOption> {
        val song = runCatching { call("getSong", "id=${enc(id)}").optJSONObject("song") }.getOrNull()
        val suffix = song?.optString("suffix").orEmpty().ifBlank { "audio" }
        val kbps = song?.optInt("bitRate") ?: 0
        val bits = song?.optInt("bitDepth") ?: 0
        val hz = song?.optInt("samplingRate") ?: 0
        val lossless = suffix.lowercase() in setOf("flac", "alac", "wav", "wave", "aiff", "aif", "ape", "wv", "dsf", "dff")
        val info = AudioInfo(format = suffix.uppercase(), sampleRateHz = hz, bitDepth = bits, kbps = kbps, lossless = lossless)
        val tier = when {
            !lossless -> "Asli"
            hz >= 176_400 -> "Hi-Res Max"
            bits > 16 || hz > 48_000 -> "Hi-Res"
            else -> "Lossless"
        }
        val out = mutableListOf(
            DownloadOption(
                title = tier,
                detail = info.detail + " · file asli tanpa konversi",
                url = url("download", "id=${enc(id)}"),
                ext = suffix,
                tag = suffix.lowercase() + if (bits > 0 && hz > 0) "-$bits-${hz / 1000}k" else ""
            )
        )
        if (lossless || kbps > 192) {
            out += DownloadOption("Standar", "MP3 · 320 kbps · dikonversi server", url("stream", "id=${enc(id)}&format=mp3&maxBitRate=320"), "mp3", "mp3-320k")
            out += DownloadOption("Hemat", "MP3 · 192 kbps · dikonversi server", url("stream", "id=${enc(id)}&format=mp3&maxBitRate=192"), "mp3", "mp3-192k")
        }
        return out
    }

    /** Untuk tombol "Tes koneksi" di Pengaturan. */
    suspend fun test(): String = withContext(Dispatchers.IO) {
        if (!configured) return@withContext "Isi alamat, username, dan password dulu."
        try {
            val r = call("ping")
            val type = r.optString("type").ifBlank { "server Subsonic" }
            val ver = r.optString("serverVersion").ifBlank { r.optString("version") }
            "Terhubung ke $type $ver".trim()
        } catch (e: IllegalArgumentException) {
            "Alamat server tidak valid (contoh: http://192.168.1.5:4533)"
        } catch (e: Exception) {
            "Gagal: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
